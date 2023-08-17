/*
 * vertigo - application development platform
 *
 * Copyright (C) 2013-2023, Vertigo.io, team@vertigo.io
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.vertigo.connectors.spring;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;

import io.vertigo.core.node.Node;
import io.vertigo.core.node.component.CoreComponent;

public class VertigoConfigBean implements BeanFactoryPostProcessor {

	@Override
	public void postProcessBeanFactory(final ConfigurableListableBeanFactory beanFactory) throws BeansException {
		Node.getNode().getComponentSpace().keySet()
				.stream()
				.forEach(key -> beanFactory.registerSingleton(key, Node.getNode().getComponentSpace().resolve(key, CoreComponent.class)));

	}

}
