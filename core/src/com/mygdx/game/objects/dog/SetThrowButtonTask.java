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

package com.mygdx.game.objects.dog;

import com.badlogic.gdx.ai.btree.Task;
import com.badlogic.gdx.ai.btree.annotation.TaskAttribute;
import com.badlogic.gdx.ai.msg.MessageManager;
import com.mygdx.game.GameMessages;
import com.mygdx.game.objects.DogCharacter;
import com.mygdx.game.objects.HumanCharacter;

/**
 * @author davebaol
 */
public class SetThrowButtonTask extends DogActionBase {

	@TaskAttribute(required=true)
	public boolean enabled;

	public SetThrowButtonTask () {
	}

	public void startAnimation(DogCharacter dog) {
	}

	@Override
	public void run () {
		DogCharacter dog = getObject();
		HumanCharacter human = dog.human;
		if (human.selected && dog.humanWantToPlay) {
			int msg = enabled ? GameMessages.GUI_SET_DOG_BUTTON_TO_THROW : GameMessages.GUI_CLEAR_DOG_BUTTON;
			boolean sendTelegram = enabled && dog.humanWantToPlay && !dog.stickThrown;
			if (!enabled)
				sendTelegram = dog.humanWantToPlay && dog.stickThrown;
			if (sendTelegram) {
				MessageManager.getInstance().dispatchMessage(msg, human);
			}
		}
		success();
	}

	@Override
	protected Task<DogCharacter> copyTo (Task<DogCharacter> task) {
		SetThrowButtonTask stbTask = (SetThrowButtonTask)task;
		stbTask.enabled = enabled;
		return super.copyTo(task);
	}

}