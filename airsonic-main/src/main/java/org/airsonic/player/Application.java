package org.airsonic.player;

import org.airsonic.player.config.AirsonicHomeConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.actuate.autoconfigure.endpoint.jmx.JmxEndpointAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.rest.RepositoryRestMvcAutoConfiguration;
import org.springframework.boot.autoconfigure.freemarker.FreeMarkerAutoConfiguration;
import org.springframework.boot.autoconfigure.groovy.template.GroovyTemplateAutoConfiguration;
import org.springframework.boot.autoconfigure.jmx.JmxAutoConfiguration;
import org.springframework.boot.autoconfigure.mail.MailSenderAutoConfiguration;
import org.springframework.boot.autoconfigure.mail.MailSenderValidatorAutoConfiguration;
import org.springframework.boot.autoconfigure.mustache.MustacheAutoConfiguration;
import org.springframework.boot.autoconfigure.websocket.servlet.WebSocketServletAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.boot.web.servlet.server.ConfigurableServletWebServerFactory;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.springframework.context.annotation.AdviceMode;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Method;

@SpringBootApplication(exclude = {
    JmxAutoConfiguration.class,
    JmxEndpointAutoConfiguration.class,
    MailSenderAutoConfiguration.class,
    MailSenderValidatorAutoConfiguration.class,
    WebSocketServletAutoConfiguration.class,
    RepositoryRestMvcAutoConfiguration.class,
    FreeMarkerAutoConfiguration.class,
    GroovyTemplateAutoConfiguration.class,
    MustacheAutoConfiguration.class
})
@EnableTransactionManagement(mode = AdviceMode.ASPECTJ)
@EnableConfigurationProperties({AirsonicHomeConfig.class})
public class Application extends SpringBootServletInitializer
        implements WebServerFactoryCustomizer<
                ConfigurableServletWebServerFactory> {


    /** Logger instance for this class. */
    private static final Logger LOG =
            LoggerFactory.getLogger(Application.class);

    /**
     * Configure the Spring application builder with necessary settings.
     * @param application the Spring application builder to configure
     * @return the configured application builder
     */
    private static SpringApplicationBuilder doConfigure(
            final SpringApplicationBuilder application) {
        // Customize the application or call application.sources(...) to
        // add sources. Since our example is itself a @Configuration class
        // (via @SpringBootApplication) we actually don't need to override
        // this method.
        return application.sources(Application.class)
                .web(WebApplicationType.SERVLET);
    }

    /**
     * Override to configure the Spring application builder.
     * @param application the Spring application builder to configure
     * @return the configured application builder
     */
    @Override
    protected SpringApplicationBuilder configure(
            final SpringApplicationBuilder application) {
        return doConfigure(application);
    }

    /**
     * Override to configure the servlet container.
     * @param container the servlet container to customize
     */
    @Override
    public void customize(
            final ConfigurableServletWebServerFactory container) {
        LOG.trace("Servlet container is {}",
                container.getClass().getCanonicalName());
        // Yes, there is a good reason we do this.
        // We cannot count on the tomcat classes being on the classpath
        // which will happen if the war is deployed to another app server
        // like Jetty. So, we ensure this class does not have any direct
        // dependencies on any Tomcat specific classes.
        try {
            Class<?> tomcatESCF = Class.forName(
                    "org.springframework.boot.web.embedded.tomcat"
                    + ".TomcatServletWebServerFactory");
            if (tomcatESCF.isInstance(container)) {
                LOG.info("Detected Tomcat web server");
                LOG.debug("Attempting to optimize tomcat");
                Object tomcatESCFInstance = tomcatESCF.cast(container);
                Class<?> tomcatApplicationClass = Class.forName(
                        "org.airsonic.player.TomcatApplication");
                Method configure = ReflectionUtils.findMethod(
                        tomcatApplicationClass, "configure", tomcatESCF);
                configure.invoke(null, tomcatESCFInstance);
                LOG.debug("Tomcat optimizations complete");
            } else {
                LOG.debug("Skipping tomcat optimization as we are not "
                        + "running on tomcat");
            }
        } catch (NoClassDefFoundError | ClassNotFoundException e) {
            LOG.debug("No tomcat classes found");
        } catch (Exception e) {
            LOG.warn("An error happened while trying to optimize tomcat", e);
        }
    }

    /**
     * Main entry point for the application.
     * @param args command line arguments
     */
    public static void main(final String[] args) {
        SpringApplicationBuilder builder = new SpringApplicationBuilder();
        doConfigure(builder).run(args);
    }

}
