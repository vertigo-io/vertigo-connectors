package io.vertigo.connectors.spring;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;

import io.vertigo.core.node.Home;
import io.vertigo.core.node.component.CoreComponent;

public class VertigoConfigBean implements BeanFactoryPostProcessor {

	@Override
	public void postProcessBeanFactory(final ConfigurableListableBeanFactory beanFactory) throws BeansException {
		Home.getApp().getComponentSpace().keySet()
				.stream()
				.forEach(key -> beanFactory.registerSingleton(key, Home.getApp().getComponentSpace().resolve(key, CoreComponent.class)));

	}

}
