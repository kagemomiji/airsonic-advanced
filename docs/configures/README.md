# Configures

Airsonic-CE has a number of configuration options.
You can configure the default options using `Java options` and `environment variables` on the first launch. 
Additionally, you can modify options by editing the configuration file `airsonic.properties` or through the `web interface`.

Some settings can only be changed by Java options. These settings are not modifiable through the web interface and the airsonic.properties file.
Please see the [Configure Airsonic using standalone](#configure-airsonic-using-standalone) and [Configure Airsonic using tomcat](#configure-airsonic-using-tomcat) sections for more details.

## airsonic.properties file

Airsonic has some system-wide configuration. These configurations are stored in the airsonic.properties file.

For more details please see this [guide](https://airsonic.github.io/docs/configure/airsonic-properties/).

## Configure Airsonic using standalone

To configure any parameters when running standalone you need to add Java parameters.

These parameters are not modifiable through the web interface.

For more details please see this [guide](https://airsonic.github.io/docs/configure/standalone/).

## Configure Airsonic using tomcat

When using tomcat to run Airsonic some parameters can be set with Java and others need to change the tomcat configuration.

These parameters are not modifiable through the web interface. See below for steps for setting Java Parameters.

For more details please see this [guide](https://airsonic.github.io/docs/configure/tomcat/).

## Detail configuration

see detail configuration [here](./detail.md)