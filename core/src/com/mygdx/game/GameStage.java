/*******************************************************************************
 * Copyright 2015 See AUTHORS file.
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/

package com.mygdx.game;


import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.ai.msg.MessageManager;
import com.badlogic.gdx.ai.msg.Telegram;
import com.badlogic.gdx.ai.msg.Telegraph;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.Ray;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Bits;
import com.badlogic.gdx.utils.IntIntMap;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.mygdx.game.objects.DogCharacter;
import com.mygdx.game.objects.GameCharacter;
import com.mygdx.game.objects.HumanCharacter;
import com.mygdx.game.objects.HumanCharacter.CharacterState;
import com.mygdx.game.settings.DebugViewSettings;
import com.mygdx.game.settings.GameSettings;
import com.mygdx.game.settings.ShaderSettings;
import com.mygdx.game.ui.*;
import com.mygdx.game.utilities.CameraController;
import com.mygdx.game.utilities.Observable;
import com.mygdx.game.utilities.Observer;

/**
 * @author jsjolund
 */
public class GameStage extends Stage implements Observable {

	private class LayerController extends Table {
		private final Slider slider;
		int minLayer = 1;
		int maxLayer = 4;

		public LayerController(TextureAtlas buttonAtlas) {
			Slider.SliderStyle sliderStyle = new Slider.SliderStyle();
			sliderStyle.knob = new TextureRegionDrawable(buttonAtlas.findRegion("slider-knob-up"));
			sliderStyle.knobDown = new TextureRegionDrawable(buttonAtlas.findRegion("slider-knob-down"));
			sliderStyle.knobOver = new TextureRegionDrawable(buttonAtlas.findRegion("slider-knob-down"));
			sliderStyle.background = new TextureRegionDrawable(buttonAtlas.findRegion("slider-background"));

			slider = new Slider(minLayer, maxLayer, 1, true, sliderStyle);
			slider.setValue(maxLayer);
			slider.setAnimateDuration(0.1f);

			slider.addCaptureListener(new ChangeListener() {
				@Override
				public void changed(ChangeEvent event, Actor actor) {
					setLayer((int) slider.getValue());
				}
			});
			add(slider).height(300);
		}

		public void setLayer(int layer) {
			layer = MathUtils.clamp(layer, minLayer, maxLayer);
			slider.setValue(layer);
			visibleLayers.clear();
			for (int i = 0; i < layer; i++) {
				visibleLayers.set(i);
			}
			notifyObserversLayerChanged(visibleLayers);
		}
	}

	public class WorldInputProcessor implements InputProcessor {

		private final Ray dragCurrentRay = new Ray();
		private final Ray lastDragProcessedRay = new Ray();
		private final Ray touchDownRay = new Ray();
		private final Ray touchUpRay = new Ray();
		private final Ray movedRay = new Ray();

		private final Vector2 lastDragProcessed = new Vector2();
		private final Vector2 cursorDelta = new Vector2();
		private final Vector2 dragCurrent = new Vector2();
		private final Vector2 keyPanDirection = new Vector2();
		private final Vector2 lastTouchDown = new Vector2();

		private final Vector3 tmp = new Vector3();
		private final Vector3 mouseCoords = new Vector3();

		private final IntIntMap keys = new IntIntMap();

		private boolean isDragging = false;

		@Override
		public boolean keyDown(int keycode) {
			keys.put(keycode, keycode);
			if (keycode == GameSettings.KEY_PAUSE) {
				speedController.setGameSpeed();
			}
			return true;
		}

		@Override
		public boolean keyUp(int keycode) {
			keys.remove(keycode, 0);
			return true;
		}

		@Override
		public boolean keyTyped(char keyChar) {
			int keyInt = Character.getNumericValue(keyChar);
			keyInt--;
			if (keyInt >= 0 && keyInt <= 9) {
				layerController.setLayer(keyInt + 1);
			}
			return true;
		}

		@Override
		public boolean touchDown(int screenX, int screenY, int pointer, int button) {
			lastTouchDown.set(screenX, screenY);
			lastDragProcessed.set(screenX, screenY);
			if (button == Input.Buttons.LEFT) {
				touchDownRay.set(viewport.getPickRay(screenX, screenY));
				cameraController.processTouchDownLeft(touchDownRay);

			} else if (button == Input.Buttons.RIGHT) {
				cameraController.processTouchDownRight();
			}
			return true;
		}

		@Override
		public boolean touchUp(int screenX, int screenY, int pointer, int button) {
			if (!isDragging) {
				touchUpRay.set(viewport.getPickRay(screenX, screenY));

				Entity hitEntity = engine.rayTest(
						touchUpRay, tmp,
						GameEngine.ALL_FLAG,
						GameEngine.ALL_FLAG,
						GameSettings.CAMERA_PICK_RAY_DST, visibleLayers);

				Gdx.app.debug(tag, "Hit entity " + hitEntity);

				if (hitEntity instanceof GameCharacter) {
					characterController.handleCharacterSelection((GameCharacter) hitEntity);

				} else if (hitEntity == engine.getScene().navmeshEntity) {
					characterController.handleCharacterPathing(touchUpRay, visibleLayers);
				}
			}
			isDragging = false;
			dragCurrent.setZero();
			lastDragProcessed.setZero();
			return true;
		}

		@Override
		public boolean touchDragged(int screenX, int screenY, int pointer) {
			dragCurrent.set(screenX, screenY);
			if (dragCurrent.dst2(lastTouchDown) > GameSettings.MOUSE_DRAG_THRESHOLD) {
				isDragging = true;
				if (Gdx.input.isButtonPressed(Input.Buttons.LEFT)) {
					dragCurrentRay.set(viewport.getPickRay(dragCurrent.x, dragCurrent.y));
					lastDragProcessedRay.set(viewport.getPickRay(lastDragProcessed.x, lastDragProcessed.y));
					cameraController.processDragPan(dragCurrentRay, lastDragProcessedRay);

				} else if (Gdx.input.isButtonPressed(Input.Buttons.RIGHT)) {
					cursorDelta.set(lastDragProcessed).sub(screenX, screenY).scl(GameSettings.MOUSE_SENSITIVITY);
					cameraController.processDragRotation(cursorDelta);
				}
				lastDragProcessed.set(screenX, screenY);
			}
			return true;
		}

		@Override
		public boolean mouseMoved(int screenX, int screenY) {
			movedRay.set(viewport.getPickRay(screenX, screenY));
			Entity e = engine.rayTest(movedRay, mouseCoords, GameEngine.NAVMESH_FLAG,
					GameEngine.NAVMESH_FLAG, GameSettings.CAMERA_PICK_RAY_DST, null);
			if (e == null) mouseCoords.set(Float.NaN, Float.NaN, Float.NaN);
			return true;
		}

		@Override
		public boolean scrolled(int amount) {
			cameraController.processZoom(amount);
			return true;
		}

		public void update(float deltaTime) {
			keyPanDirection.setZero();
			if (keys.containsKey(GameSettings.KEY_PAN_FORWARD)) {
				keyPanDirection.y++;
			}
			if (keys.containsKey(GameSettings.KEY_PAN_BACKWARD)) {
				keyPanDirection.y--;
			}
			if (keys.containsKey(GameSettings.KEY_PAN_LEFT)) {
				keyPanDirection.x--;
			}
			if (keys.containsKey(GameSettings.KEY_PAN_RIGHT)) {
				keyPanDirection.x++;
			}
			if (!keyPanDirection.isZero()) {
				keyPanDirection.nor();
				cameraController.processKeyboardPan(keyPanDirection, deltaTime);
			}
		}
	}


	private class CharacterController extends Table implements Telegraph {
		private class CharacterButton extends ImageButton {

			CharacterState state;

			CharacterButton(HumanCharacter.CharacterState state, TextureAtlas buttonAtlas, String up, String down, String checked) {
				super(new TextureRegionDrawable(buttonAtlas.findRegion(up)),
						new TextureRegionDrawable(buttonAtlas.findRegion(down)),
						new TextureRegionDrawable(buttonAtlas.findRegion(checked)));
				this.state = state;
				this.addListener(new ClickListener() {
					@Override
					public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
						if (!CharacterButton.this.isChecked()) {
							if (selectedCharacter instanceof HumanCharacter) {
								HumanCharacter human = (HumanCharacter) selectedCharacter;
								CharacterState hs = (CharacterState)human.stateMachine.getCurrentState();
								if (CharacterButton.this.state.isMovementState()) {
									if (hs.isIdleState()) {
										human.moveState = CharacterButton.this.state;
										human.handleStateCommand(CharacterButton.this.state.idleState);
									}
									else if (!hs.isMovementState()) {
										human.moveState = CharacterButton.this.state;
										human.handleStateCommand(CharacterButton.this.state.idleState);
									}
									else {
										human.handleStateCommand(CharacterButton.this.state);
									}
								}
								else {
									human.handleStateCommand(CharacterButton.this.state);
								}
							}
						}
						return true;
					}
				});
			}
		}

		private ButtonGroup<CharacterButton> radioGroup;
		private CharacterButton whistleButton;
		private CharacterButton throwButton;
		private Cell<CharacterButton> firstCell;
		private GameCharacter selectedCharacter;

		public CharacterController(TextureAtlas buttonAtlas) {
			whistleButton = new CharacterButton(HumanCharacter.CharacterState.WHISTLE, buttonAtlas, "whistle-up", "whistle-down", "whistle-down");
			throwButton = new CharacterButton(HumanCharacter.CharacterState.THROW, buttonAtlas, "throw-up", "throw-down", "throw-down");

			radioGroup = new ButtonGroup<CharacterButton>(
				new CharacterButton(HumanCharacter.CharacterState.MOVE_RUN, buttonAtlas, "run-up", "run-down", "run-down"),
				new CharacterButton(HumanCharacter.CharacterState.MOVE_WALK, buttonAtlas, "walk-up", "walk-down", "walk-down"),
				new CharacterButton(HumanCharacter.CharacterState.MOVE_CROUCH, buttonAtlas, "crouch-up", "crouch-down", "crouch-down"),
				//new CharacterButton(HumanCharacter.CharacterState.MOVE_CRAWL, buttonAtlas, "crawl-up", "crawl-down", "crawl-down"),
				new CharacterButton(HumanCharacter.CharacterState.DEAD, buttonAtlas, "kill-up", "kill-down", "kill-down")
			);

			// Add whistle button and save the reference to the 1st cell
			this.firstCell = add(whistleButton);

			// Add radio buttons
			for (CharacterButton btn : radioGroup.getButtons()) {
				add(btn);
			}
			
			// Register this controller's interests
			MessageManager.getInstance().addListeners(this,
				GameMessages.GUI_CLEAR_1ST_RADIO_BUTTON,
				GameMessages.GUI_SET_1ST_RADIO_BUTTON_TO_WHISTLE,
				GameMessages.GUI_SET_1ST_RADIO_BUTTON_TO_THROW);
		}
		
		private final void setFirstRadioButton(CharacterButton btn, HumanCharacter human) {
			if (human != null && human == selectedCharacter) {
				whistleButton.setVisible(false);
				throwButton.setVisible(false);
				firstCell.setActor(btn);
				btn.setVisible(true);
				btn.setChecked(false);
				System.out.println("setFirstRadioButton = " + (btn == whistleButton ? "whistleButton" : (btn == throwButton ? "throwButton" : "???")));
			}
		}
		
		private final void clearFirstRadioButton(HumanCharacter human) {
			if (human != null && human == selectedCharacter) {
				whistleButton.setVisible(false);
				throwButton.setVisible(false);
			}
		}

		public void handleCharacterSelection(GameCharacter character) {
			if (selectedCharacter == character)
				return;

			// Remove selection from previously selected human (if any)
			if (selectedCharacter != null && selectedCharacter instanceof HumanCharacter) {
				HumanCharacter human = (HumanCharacter) selectedCharacter;
				human.selected = false;
			}
			
			// Select new character
			selectedCharacter = character;
			notifyObserversEntitySelected(selectedCharacter);

			if (character instanceof HumanCharacter) {
				steerSettings.clearChildren();
				steerSettings.add(humanSteerSettings);
				steerSettings.invalidateHierarchy();

				HumanCharacter human = (HumanCharacter) character;
				human.selected = true;
				// Restore the controller based on the newly selected human
				if (human.dog != null) {
					if (!human.dog.humanWantToPlay) {
						this.setFirstRadioButton(whistleButton, human);
					}
					else if (!human.dog.stickThrown) {
						this.setFirstRadioButton(throwButton, human);
					}
					else {
						this.clearFirstRadioButton(human);
					}
				}
				else {
					this.clearFirstRadioButton(human);
				}
				for (CharacterButton btn : radioGroup.getButtons()) {
					if (btn.state == CharacterState.DEAD && human.stateMachine.getCurrentState() == CharacterState.DEAD) {
						btn.setChecked(true);
						break;
					}
					if (btn.state == human.getCurrentMoveState())
						btn.setChecked(true);
				}
				this.setVisible(true);
			} else if (character instanceof DogCharacter) {
				steerSettings.clearChildren();
				steerSettings.add(dogSteerSettings);
				steerSettings.invalidateHierarchy();
				this.setVisible(false);  // no controller for dogs
			}
		}

		public void handleCharacterPathing(Ray ray, Bits visibleLayers) {
			// Perform pathfinding only if a human character is selected and a movement button is checked
			if (selectedCharacter instanceof HumanCharacter && radioGroup.getCheckedIndex() > -1 && radioGroup.getChecked().state.isMovementState()) {
				if (engine.getScene().navMesh.getPath(selectedCharacter.currentTriangle,
						selectedCharacter.getGroundPosition(tmp),
						ray, visibleLayers,
						GameSettings.CAMERA_PICK_RAY_DST,
						selectedCharacter.navMeshGraphPath)) {

					selectedCharacter.calculateNewPath();
				}
			}
		}

		@Override
		public boolean handleMessage (Telegram telegram) {
			switch (telegram.message) {
			case GameMessages.GUI_SET_1ST_RADIO_BUTTON_TO_WHISTLE:
				setFirstRadioButton(whistleButton, (HumanCharacter)telegram.extraInfo);
				break;
			case GameMessages.GUI_SET_1ST_RADIO_BUTTON_TO_THROW:
				setFirstRadioButton(throwButton, (HumanCharacter)telegram.extraInfo);
				break;
			case GameMessages.GUI_CLEAR_1ST_RADIO_BUTTON:
				clearFirstRadioButton((HumanCharacter)telegram.extraInfo);
				break;
			}
			return true;
		}
	}

	public static final String tag = "GameStage";
	private final Viewport viewport;
	private final SpriteBatch batch;
	private final ShapeRenderer shapeRenderer;
	private final Table rootTable;
	private final TextureAtlas buttonsAtlas;
	private final ValueLabel<Vector3> mouseCoordsLabel;
	private final Camera cameraUI;
	private final Camera camera3D;
	private final Skin skin;
	private final CameraController cameraController;
	private final GameEngine engine;
	private final Array<Observer> observers = new Array<Observer>();
	private final WorldInputProcessor worldInputProcessor;
	private final Label fpsLabel;
	private final GameSpeedController speedController;
	private final CharacterController characterController;
	private final LayerController layerController;
	private final FloatSettingsMenu humanSteerSettings;
	private final FloatSettingsMenu dogSteerSettings;
	private final Table steerSettings;

	private final Vector3 tmp = new Vector3();
	private Bits visibleLayers;

	public GameStage(GameEngine engine, Viewport viewport, CameraController cameraController) {
		super(viewport);
		this.engine = engine;
		this.viewport = viewport;
		this.camera3D = viewport.getCamera();
		this.cameraController = cameraController;

		visibleLayers = new Bits();

		InputMultiplexer multiplexer = new InputMultiplexer();
		multiplexer.addProcessor(this);
		multiplexer.addProcessor(worldInputProcessor = new WorldInputProcessor());
		Gdx.input.setInputProcessor(multiplexer);

		cameraUI = new OrthographicCamera(viewport.getScreenWidth(), viewport.getScreenHeight());
		cameraUI.position.set(viewport.getScreenWidth() / 2, viewport.getScreenHeight() / 2, 0);
		cameraUI.update();

		batch = new SpriteBatch();
		shapeRenderer = new ShapeRenderer();
		shapeRenderer.setAutoShapeType(true);
		skin = new Skin(Gdx.files.internal("skins/uiskin.json"));

		buttonsAtlas = new TextureAtlas(Gdx.files.internal("skins/buttons.atlas"));

		mouseCoordsLabel = new ValueLabel<Vector3>("NavMesh: ", new Vector3(), skin) {
			@Override
			public Vector3 getValue() {
				return worldInputProcessor.mouseCoords;
			}

			@Override
			public void copyValue(Vector3 newValue, Vector3 oldValue) {
				oldValue.set(newValue);
			}
		};
		fpsLabel = new IntValueLabel("FPS: ", 999, skin) {
			@Override
			public int getValue() {
				return Gdx.graphics.getFramesPerSecond();
			}
		};
		speedController = new GameSpeedController(buttonsAtlas);
		characterController = new CharacterController(buttonsAtlas);
		characterController.setVisible(false); // initially no character is selected, so hide the controller
		layerController = new LayerController(buttonsAtlas);
		layerController.setLayer(Integer.MAX_VALUE);

		rootTable = new Table();
		addActor(rootTable);
		rootTable.setDebug(true, true);

		Table topTable = new Table();
		topTable.add(fpsLabel).width(fpsLabel.getWidth()).top().left();
		topTable.add(mouseCoordsLabel).padLeft(15).top().left();
		topTable.add(new Table()).expandX().fillX();
		rootTable.add(topTable).expandX().fillX();

		rootTable.row();
		rootTable.add(new Table()).expandY().fillY();
		rootTable.row();

		humanSteerSettings = new FloatSettingsMenu("steering (human)", skin,
				HumanCharacter.HumanSteerSettings.class);
		dogSteerSettings = new FloatSettingsMenu("steering (dog)", skin,
				DogCharacter.DogSteerSettings.class);
		steerSettings = new Table();
		steerSettings.add(humanSteerSettings);

		Table bottomRightTable = new Table();
		bottomRightTable.add(new FloatSettingsMenu("shader", skin, ShaderSettings.class)).bottom();
		bottomRightTable.add(new BooleanSettingsMenu("debug", skin, DebugViewSettings.class)).bottom();
		bottomRightTable.add(steerSettings).bottom();
		bottomRightTable.add(new Table()).expandX().fillX().bottom();
		rootTable.add(bottomRightTable).expandX().fillX();

		Table bottomLeftTable = new Table();
		bottomLeftTable.add(layerController).right().colspan(2);
		bottomLeftTable.row();
		bottomLeftTable.add(characterController);
		bottomLeftTable.add(speedController);
		bottomLeftTable.setTransform(true);
		bottomLeftTable.setOrigin(bottomLeftTable.getPrefWidth(), 0);
		bottomLeftTable.setScale(0.5f);
		rootTable.add(bottomLeftTable).bottom();

		rootTable.row();
		rootTable.left().bottom();

		getRoot().addCaptureListener(new InputListener() {
			public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
				if (!(event.getTarget() instanceof TextField)) setKeyboardFocus(null);
				return false;
			}
		});
	}


	public Bits getVisibleLayers(Bits out) {
		out.clear();
		out.or(visibleLayers);
		return out;
	}

	@Override
	public void dispose() {
		super.dispose();
		batch.dispose();
		skin.dispose();
		shapeRenderer.dispose();
		buttonsAtlas.dispose();
	}

	public void resize(int width, int height) {
		getViewport().update(width, height, false);
		cameraUI.viewportWidth = viewport.getScreenWidth();
		cameraUI.viewportHeight = viewport.getScreenHeight();
		cameraUI.position.set(viewport.getScreenWidth() / 2, viewport.getScreenHeight() / 2, 0);
		cameraUI.update();
		batch.setProjectionMatrix(cameraUI.combined);
		shapeRenderer.setProjectionMatrix(cameraUI.combined);

		rootTable.setSize(viewport.getScreenWidth(), viewport.getScreenHeight());
	}


	@Override
	public Vector2 screenToStageCoordinates(Vector2 screenCoords) {
		tmp.set(screenCoords.x, screenCoords.y, 1);
		cameraUI.unproject(tmp, viewport.getScreenX(), viewport.getScreenY(),
				viewport.getScreenWidth(), viewport.getScreenHeight());
		screenCoords.set(tmp.x, tmp.y);
		return screenCoords;
	}

	@Override
	public void act(float delta) {
		super.act(delta);
		worldInputProcessor.update(delta);
	}

	@Override
	public void draw() {
		batch.begin();
		for (Actor actor : getActors()) {
			if (actor.isVisible()) {
				actor.draw(batch, 1);
			}
		}
		batch.end();

		if (DebugViewSettings.drawUIDebug) {
			shapeRenderer.begin();
			rootTable.drawDebug(shapeRenderer);
			shapeRenderer.end();
		}
	}

	@Override
	public void addObserver(Observer observer) {
		observers.add(observer);
	}

	@Override
	public void removeObserver(Observer observer) {
		observers.removeValue(observer, true);
	}

	@Override
	public void notifyObserversEntitySelected(GameCharacter entity) {
		for (Observer observer : observers) {
			observer.notifyEntitySelected(entity);
		}
	}

	@Override
	public void notifyObserversLayerChanged(Bits layer) {
		for (Observer observer : observers) {
			observer.notifyLayerChanged(layer);
		}
	}
}
