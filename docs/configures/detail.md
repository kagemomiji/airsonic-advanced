# Detail Configuration

## airsonic.home

The directory where Airsonic will store its configuration data.  
This directory will be created if it does not exist.  
Docker image does not support this option. Docker image will use the `$AIRSONIC_DIR/airsonic` directory.

| item | description |
| --- | --- |
| type | string |
| default | (Windows) C:\\airsonic, (Other) /var/airsonic |
| example | airsonic.home=/var/airsonic |
| configurable by | Java options, environment variables |
| environment variable | AIRSONIC_HOME |

## airsonic.defaultMusicFolder

The directory where Airsonic will look for music.   
This is only used when initializing the database for the first time.  
Docker image does not support this option. Docker image will use the `$AIRSONIC_DIR/music` directory.

| item | description |
| --- | --- |
| type | string |
| default | (Windows) C:\\Music, (Other) /var/music |
| example | airsonic.defaultMusicFolder=/var/music |
| configurable by | Java options, environment variables |
| environment variable | AIRSONIC_DEFAULTMUSICFOLDER |

## airsonic.defaultPodcastFolder

The directory where Airsonic will look for podcasts.
This is only used when initializing the database for the first time.  
Docker image does not support this option. Docker image will use the `$AIRSONIC_DIR/podcasts` directory.

| item | description |
| --- | --- |
| type | string |
| default | (Windows) C:\\Podcasts, (Other) /var/podcasts |
| example | airsonic.defaultMusicFolder=/var/music |
| configurable by | Java options, environment variables |
| environment variable | AIRSONIC_DEFAULTMUSICFOLDER |

## airsonic.defaultPlaylistFolder

The directory where Airsonic will look for playlists.  
This is only used when initializing the database for the first time.
Docker image does not support this option. Docker image will use the `$AIRSONIC_DIR/playlists` directory.

| item | description |
| --- | --- |
| type | string |
| default | (Windows) C:\\Playlists, (Other) /var/playlists |
| example | airsonic.defaultPlaylistFolder=/var/playlists |
| configurable by | Java options, environment variables |
| environment variable | AIRSONIC_DEFAULTPLAYLISTFOLDER |

## airsonic.hide-virtual-tracks

If enabled, Airsonic will hide virtual tracks media file list.
This is only used when initializing the database for the first time.

| item | description |
| --- | --- |
| type | boolean |
| default | true |
| example | airsonic.hide-virtual-tracks=false |
| configurable by | Java options, environment variables, airsonic.properties, web interface|
| environment variable | AIRSONIC_HIDEVIRTUALTRACKS |
| airsonic.properties | HIDE_VIRTUAL_TRACKS |
| web interface | Settings > Music Folder > Hide virtual tracks |
| support version | `>= v11.1.4` |

## airsonic.cue.enabled

If enabled, Airsonic-CE will look for cue sheets in the same directory as the audio file and automatically split the audio file into tracks.
Configuration by Java options, environment variables are working as default value.

> **Note**  
> If you changed the value of this option, you need to re-scan the music folder with `FullScan` option.

| item | description |
| --- | --- |
| type | boolean |
| default | true |
| example | airsonic.cue.enabled=true |
| configurable by | Java options, environment variables, airsonic.properties, web interface |
| environment variable | AIRSONIC_CUE_ENABLED |
| airsonic.properties | ENABLE_CUE_INDEXING |
| web interface | Settings > Music Folder > Enable cue indexing |

## airsonic.cue.hide-indexed-files

If enabled, Airsonic-CE will hide the original audio file when cue sheet support is enabled.  
Configuration by Java options, environment variables are working as default value.

| item | description |
| --- | --- |
| type | boolean |
| default | true |
| example | airsonic.cue.hide-indexed-files=true |
| configurable by | Java options, environment variables, airsonic.properties, web interface |
| environment variable | AIRSONIC_CUE_HIDEINDEXEDFILES |
| airsonic.properties | HIDE_INDEXED_FILES |
| web interface | Settings > Music Folder > Hide cue-indexed files |
| support version | `<= v11.1.3` |


## airsonic.scan.full-timeout

The maximum time in seconds that Airsonic will spend scanning  media folders when FullScan is enabled.

| item | description |
| --- | --- |
| type | integer |
| default | 14400 |
| example | airsonic.scan.full-timeout=3600 |
| configurable by | Java options, environment variables |
| environment variable | AIRSONIC_SCAN_FULLTIMEOUT |


## airsonic.scan.timeout

The maximum time in seconds that Airsonic will spend scanning  media folders when FullScan is disabled.

| item | description |
| --- | --- |
| type | integer |
| default | 3600 |
| example | airsonic.scan.timeout=600 |
| configurable by | Java options, environment variables |
| environment variable | AIRSONIC_SCAN_TIMEOUT |

## airsonic.scan.parallelism

The number of parallel threads that Airsonic will use when scanning media folders.  
MediaScannerParallelism is deprecated. Please use this option instead.


| item | description |
| --- | --- |
| type | integer |
| default | the number of CPU processors + 1 |
| example | airsonic.scan.parallelism=4 |
| configurable by | Java options, environment variables, airsonic.properties |
| environment variable | AIRSONIC_SCAN_PARALLELISM |
| airsonic.properties | AIRSONIC_SCAN_PARALLELISM |
