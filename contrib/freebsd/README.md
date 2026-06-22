# Running Airsonic-Advanced on FreeBSD

FreeBSD does not use systemd. This directory ships an `rc.d` service script
(`airsonic`) that is the FreeBSD counterpart to `contrib/airsonic.service`. It
exposes the same knobs, only as `rc.conf` variables.

The instructions below assume a stand-alone `.war` (see the *Stand-alone
binaries* section of the main README), not the Docker image.

## 1. Install a JRE

Airsonic-Advanced 11.x needs Java 17 or newer:

```sh
pkg install openjdk21
```

> **Note:** FreeBSD installs each JDK under `/usr/local/openjdkNN/bin/java`.
> The generic `/usr/local/bin/java` wrapper may resolve to an *older* JDK if
> several are installed (e.g. `openjdk8`), which Airsonic will refuse to run on.
> When in doubt, pin `airsonic_java` to the explicit path (see below).

## 2. Create a dedicated user

```sh
pw groupadd airsonic
pw useradd airsonic -g airsonic -d /var/airsonic -s /usr/sbin/nologin -c "Airsonic Media Server"
```

## 3. Place the war and data directory

```sh
mkdir -p /var/airsonic
fetch -o /var/airsonic/airsonic.war \
  https://github.com/kagemomiji/airsonic-advanced/releases/latest/download/airsonic.war
chown -R airsonic:airsonic /var/airsonic
```

## 4. Install the service script

```sh
install -m 0755 contrib/freebsd/airsonic /usr/local/etc/rc.d/airsonic
```

## 5. Configure and enable

Add the settings you need to `/etc/rc.conf` (only `airsonic_enable` is
required, everything else has a sensible default):

```sh
sysrc airsonic_enable="YES"
sysrc airsonic_java="/usr/local/openjdk21/bin/java"
sysrc airsonic_java_opts="-Xmx1g"
sysrc airsonic_port="4040"
sysrc airsonic_context_path="/"
```

Then start it:

```sh
service airsonic start
service airsonic status
```

## Available rc.conf variables

| Variable                  | Default                      | systemd equivalent          |
|---------------------------|------------------------------|-----------------------------|
| `airsonic_enable`         | `NO`                         | (enable the unit)           |
| `airsonic_home`           | `/var/airsonic`              | `AIRSONIC_HOME`             |
| `airsonic_war`            | `${airsonic_home}/airsonic.war` | `JAVA_JAR`               |
| `airsonic_user`           | `airsonic`                   | `User`                      |
| `airsonic_group`          | `airsonic`                   | `Group`                     |
| `airsonic_java`           | `/usr/local/bin/java`        | (the `java` binary)         |
| `airsonic_java_opts`      | `-Xmx700m`                   | `JAVA_OPTS`                 |
| `airsonic_port`           | `8080`                       | `PORT`                      |
| `airsonic_context_path`   | `/airsonic`                  | `CONTEXT_PATH`              |
| `airsonic_args`           | (empty)                      | `JAVA_ARGS`                 |
| `airsonic_log`            | `/var/log/airsonic.log`      | (journald)                  |

## Behind a reverse proxy

Airsonic talks to its Web UI over websockets, so the proxy has to allow
`Upgrade` requests, and the server needs `server.forward-headers-strategy=native`.
This is not FreeBSD-specific — see the *Compatibility Notes* in the main README.
You can pass the property through `airsonic_args`, e.g.:

```sh
sysrc airsonic_args="--server.forward-headers-strategy=native"
```
