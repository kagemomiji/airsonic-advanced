# Setting up jukebox player

## Table of content

- [Finding device name](#finding-device-name)
- [Using sound.properties](#using-soundproperties)
- [Using Java parameters](#using-java-parameters)
- [Using Systemd](#using-systemd)
- [Using Docker](#using-docker)
- [Use Jukebox with Pulseaudio](#use-jukebox-with-pulseaudio)

Jukebox might not always work out-of-the-box and may require some additional tweaking. If you get no sound output while trying to play via the Jukebox, you might need to tweak the audio device being picked up by Java sound.

## Finding device name

You can run the folowing Java program to get a list of all the audio devices in your system:

```java
import java.io.*;
import javax.sound.sampled.*;

public class audioDevList {
    public static void main(String args[]) {
        Mixer.Info[] mixerInfo =
            AudioSystem.getMixerInfo();
            System.out.println("Available mixers:");
            for(int cnt = 0; cnt < mixerInfo.length;cnt++) {
                System.out.println(mixerInfo[cnt].getName());
        }
    }
}
```

Sample output:

```bash
Available mixers:
Port HDMI [hw:0]
Port PCH [hw:1]
default [default]
HDMI [plughw:0,3]
HDMI [plughw:0,7]
HDMI [plughw:0,8]
HDMI [plughw:0,9]
HDMI [plughw:0,10]
PCH [plughw:1,0]
```

## Using sound.properties

You can then generate a sound.properties file accordingly with your devicename:

```text
javax.sound.sampled.Clip=#PCH [plughw:1,0]
javax.sound.sampled.Port=#Port PCH [hw:1]
javax.sound.sampled.SourceDataLine=#PCH [plughw:1,0]
javax.sound.sampled.TargetDataLine=#PCH [plughw:1,0]
```

Copy the sound.properties file to /etc/java-17-openjdk/sound.properties. Change java-17-openjdk depending on your java installation.

## Using Java parameters

You can pass the devicename as parameter into the launch script/service file:

```bash
-Djavax.sound.sampled.Clip=#PCH [plughw:1,0]
-Djavax.sound.sampled.Port=#Port PCH [hw:1]
-Djavax.sound.sampled.SourceDataLine=#PCH [plughw:1,0]
-Djavax.sound.sampled.TargetDataLine=#PCH [plughw:1,0]
```

## Using Systemd

You should change some parameters in the systemd service file:

```text
[Unit]
Description=Airsonic Media Server
After=remote-fs.target network.target
AssertPathExists=/var/airsonic

[Service]
Type=simple
Environment="JAVA_JAR=/var/airsonic/airsonic.war"
Environment="JAVA_OPTS="
Environment="AIRSONIC_HOME=/var/airsonic"
Environment="PORT=8082"
Environment="CONTEXT_PATH=/"
Environment="JAVA_ARGS="
ExecStart=/usr/bin/java \
-Dserver.forward-headers-strategy=native \
${JAVA_OPTS} \
-Dairsonic.home=${AIRSONIC_HOME} \
-Dserver.servlet.context-path=${CONTEXT_PATH} \
-Djavax.sound.sampled.Clip='#PCH [plughw:1,0]' \ #please set your device name here
-Djavax.sound.sampled.Port='#Port PCH [hw:1]' \ #please set your device name here 
-Djavax.sound.sampled.SourceDataLine='#PCH [plughw:1,0]' \ #please set your device name here
-Djavax.sound.sampled.TargetDataLine='#PCH [plughw:1,0]' \ #please set your device name here
-Dserver.port=${PORT} \
-jar ${JAVA_JAR} ${JAVA_ARGS}

User=airsonic
Group=airsonic

# See https://www.freedesktop.org/software/systemd/man/systemd.exec.html
# for detail
DevicePolicy=auto  #please set auto
DeviceAllow=char-alsa rw #please allow alsa
NoNewPrivileges=yes
#PrivateDevices=yes #comment out
PrivateTmp=yes
PrivateUsers=yes
ProtectControlGroups=yes
ProtectKernelModules=yes
ProtectKernelTunables=yes
RestrictAddressFamilies=AF_UNIX AF_INET AF_INET6
RestrictNamespaces=yes
RestrictRealtime=yes
SystemCallFilter=~@clock @debug @module @mount @obsolete @privileged @reboot @setuid @swap
ReadWritePaths=/var/airsonic

# You can change the following line to `strict` instead of `full`
# if you don't want airsonic to be able to
# write anything on your filesystem outside of AIRSONIC_HOME.
ProtectSystem=full #set full

# You can uncomment the following line if you don't have any media
# in /home/…. This will prevent airsonic from ever reading/writing anything there.
ProtectHome=false

# You can uncomment the following line if you're not using the OpenJDK.
# This will prevent processes from having a memory zone that is both writeable
# and executeable, making hacker's lifes a bit harder.
#MemoryDenyWriteExecute=yes


[Install]
WantedBy=multi-user.target

```

## Using Docker

Ensure that the docker user (passed through --user in the docker run) command has access to the /dev/snd device. Typically this can be done on most distros by adding the user to the audio group. You can alternatively use the --group-add flag to add the audio group the the user.
Pass the --device /dev/snd argument for docker run. See the docker documentation for more details.
You can mount a copy of the previous sound.properties file to /etc/java-17-openjdk/sound.properties inside the container.
All of the above might result in an invocation like the following:

docker run \
    -v /home/airsonic/music:/music \
    -v /home/airsonic/config:/config \
    -v /home/airsonic/podcasts:/podcasts \
    -v /home/airsonic/playlists:/playlists \
    --group-add audio \
    --device /dev/snd \
    -v /home/airsonic/sound.properties:/opt/java/openjdk/conf/sound.properties \
    -p 4040:4040 \
    ghcr.io/airsonic-ce/airsonic-ce

## Use Jukebox with Pulseaudio

The point of this configuration is to force pulseaudio to use mixed ALSA output alsa_output.dmix (if it’s available in your system). To check what sink is being used use pactl list sinks.

Configure java machine as stated above to get Jukebox working

Configure pulseaudio alsa module to use dmix device by default (remember to edit an apropriate *.pa file, /etc/pulse/default.pa if your pulseaudio instance is being autospawn by clients or /etc/pulse/system.pa if you run pulseaudio in system mode):

load-module module-alsa-sink device=dmix
load-module module-alsa-source device=snoop
Configure pulseaudio to use dmix output by default.

set-default-sink asla_output.dmix