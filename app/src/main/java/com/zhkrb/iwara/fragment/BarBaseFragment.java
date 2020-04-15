/*
 * Copyright zhkrb
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
 *
 * Create by zhkrb on 2020/3/1 20:50
 */

package com.zhkrb.iwara.fragment;

import android.content.Context;
import android.os.Bundle;
import android.view.View;

import com.zhkrb.iwara.base.AbsFragment;

public abstract class BarBaseFragment extends AbsFragment {

    public abstract View getTopAppbar(Context context);

    public abstract boolean getNeedFixTop();
}