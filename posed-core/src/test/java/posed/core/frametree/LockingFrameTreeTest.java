/*
 * Copyright (C) 2019, Scott Dial, All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package posed.core.frametree;

import org.junit.Test;
import org.orekit.frames.Frame;
import org.orekit.frames.FramesFactory;

public class LockingFrameTreeTest extends AbstractFrameTreeTest {
    @Override
    protected LockingFrameTree newFrameTree(Frame root) {
        return new LockingFrameTree(root);
    }

    @Test
    public void testLock() {
        LockingFrameTree tree = newFrameTree(FramesFactory.getGCRF());
        tree.lock();
        tree.unlock();
    }
}