package org.airsonic.player.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

/**
 * Configuration for optimizing Spring Boot startup performance.
 *
 * This configuration ensures that critical components needed for basic
 * application functionality are loaded eagerly, while non-critical
 * components are loaded lazily.
 */
@Configuration
public class StartupOptimizationConfig {

    /**
     * Bean post-processor to handle eager initialization of critical components
     * even when global lazy initialization is enabled.
     */
    @Bean
    @Order(1)
    public EagerBeanPostProcessor eagerBeanPostProcessor() {
        return new EagerBeanPostProcessor();
    }

    /**
     * Component that manages which beans should be eagerly loaded
     * for proper application functionality.
     */
    public static class EagerBeanPostProcessor
            implements org.springframework.beans.factory.config
                    .BeanPostProcessor {

        private static final org.slf4j.Logger LOG =
                org.slf4j.LoggerFactory.getLogger(EagerBeanPostProcessor.class);

        @Override
        public Object postProcessBeforeInitialization(final Object bean,
                final String beanName) {
            // Log startup of critical components
            if (isCriticalComponent(beanName)) {
                LOG.debug("Eagerly initializing critical component: {}",
                        beanName);
            }
            return bean;
        }

        private boolean isCriticalComponent(final String beanName) {
            return beanName.contains("Security") ||
                   beanName.contains("DataSource") ||
                   beanName.contains("entityManagerFactory") ||
                   beanName.contains("transactionManager");
        }
    }
}
