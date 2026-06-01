# Cover Art/ Artist Image

Airsonic-CE supports cover art and artist image for your music.

## Configuration

Settings for importing cover art and artist images can be found in the WebUI under `Settings` > `General`.

| item | Description | Default Value |
| --- | --- | --- |
| Cover art files | The image file names and extensions to be used as cover art, separated by spaces.| cover.jpg cover.png cover.gif folder.jpg jpg jpeg gif png |
| Cover art source | The source for obtaining cover art.| Prefer external file over embedded image |
| Cover art quality | The quality of the cover art.| 90 |
| Cover art concurrency | Specify the number of cover art thumbnails that can be generated simultaneously (if needed). Higher number means more thumbs can be generated simultaneously, but requires more CPU threads/cores. This setting requires a restart before it takes effect.| 4 |


## How it works

Airsonic Advanced handles cover art and artist images under the assumption that files are saved in an artist/album/song folder structure as follows:

### Cover Art for Music Files

Airsonic Advanced handles cover art as follows:

1. Retrieve cover art according to the method specified in Cover art source:
  - Prefer embedded image over external file: Prioritize the embedded cover art in the file.
  - Prefer external file over embedded image: Prioritize the image in the same directory as the file.
  - Use only embedded image: Use only the embedded cover art in the file.
  - Use only external file: Use only the image in the same directory as the file.
2. If cover art is not found using the above methods, the default cover art is used.

### Cover Art for Album

The album cover art uses the cover art from one of the music files directly under the album.

### Artist Images

Artist images use images with the `Cover Art files` in the artist directory. If none are found, a default artist image is generated.
If you set the `Cover art source` to `Use only embedded image`, the artist image will not use files in the artist directory.
