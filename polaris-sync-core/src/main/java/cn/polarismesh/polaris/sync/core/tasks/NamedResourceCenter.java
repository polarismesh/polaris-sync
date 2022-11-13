/*
 * Tencent is pleased to support the open source community by making Polaris available.
 *
 * Copyright (C) 2019 THL A29 Limited, a Tencent company. All rights reserved.
 *
 * Licensed under the BSD 3-Clause License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://opensource.org/licenses/BSD-3-Clause
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package cn.polarismesh.polaris.sync.core.tasks;

import cn.polarismesh.polaris.sync.extension.ResourceCenter;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public class NamedResourceCenter<C extends ResourceCenter> {

	private final String name;

	private final String productName;

	private final C center;

	public NamedResourceCenter(String name, String productName,
			C center) {
		this.name = name;
		this.productName = productName;
		this.center = center;
	}

	public String getProductName() {
		return productName;
	}

	public String getName() {
		return name;
	}

	public C getCenter() {
		return center;
	}

}
