package io.vertigo.connectors.spring;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;

import io.vertigo.core.node.App;
import io.vertigo.core.node.component.CoreComponent;

public class VertigoConfigBean implements BeanFactoryPostProcessor {

	@Override
	public void postProcessBeanFactory(final ConfigurableListableBeanFactory beanFactory) throws BeansException {
		App.getApp().getComponentSpace().keySet()
				.stream()
				.forEach(key -> beanFactory.registerSingleton(key, App.getApp().getComponentSpace().resolve(key, CoreComponent.class)));

	}

}
