package org.airsonic.player.service;

import org.airsonic.player.config.AirsonicHomeConfig;
import org.airsonic.player.domain.Transcoding;
import org.airsonic.player.repository.PlayerRepository;
import org.airsonic.player.repository.TranscodingRepository;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.*;

@SpringBootTest
@EnableConfigurationProperties({AirsonicHomeConfig.class})
public class TranscodingServiceIntTest {

    @Autowired
    private TranscodingService transcodingService;
    @MockitoSpyBean
    private PlayerRepository playerRepository;
    @MockitoSpyBean
    private TranscodingRepository transcodingRepository;

    @TempDir
    private static Path tempDir;

    @BeforeAll
    public static void init() {
        System.setProperty("airsonic.home", tempDir.toString());
    }


    @Test
    public void defaultValueTest() {
        List<Transcoding> transcodings = transcodingService.getAllTranscodings();
        // mp3 audio
        Transcoding mp3Transcode = transcodings.stream().filter(t -> t.getName().equals("mp3 audio")).findFirst().get();
        assertNotNull(mp3Transcode);
        assertEquals("ogg oga aac m4a flac wav wma aif aiff ape mpc shn wv", mp3Transcode.getSourceFormats());
        assertEquals("ffmpeg %S -i %s -map 0:0 -b:a %bk -v 0 -f mp3 -", mp3Transcode.getStep1());
        assertEquals("mp3", mp3Transcode.getTargetFormat());
        assertNull(mp3Transcode.getStep2());
        assertNull(mp3Transcode.getStep3());
        assertEquals("mp3", mp3Transcode.getTargetFormat());

        //"flv/h264 video"
        Transcoding flvH264Transcode = transcodings.stream().filter(t -> t.getName().equals("flv/h264 video")).findFirst().get();
        assertNotNull(flvH264Transcode);
        assertEquals("avi mpg mpeg mp4 m4v mkv mov wmv ogv divx m2ts", flvH264Transcode.getSourceFormats());
        assertEquals("ffmpeg -ss %o -i %s -async 1 -b %bk -s %wx%h -ar 44100 -ac 2 -v 0 -f flv -vcodec libx264 -preset superfast -threads 0 -", flvH264Transcode.getStep1());
        assertNull(flvH264Transcode.getStep2());
        assertNull(flvH264Transcode.getStep3());
        assertEquals("flv", flvH264Transcode.getTargetFormat());

        //"mkv video"
        Transcoding mkvTranscode = transcodings.stream().filter(t -> t.getName().equals("mkv video")).findFirst().get();
        assertNotNull(mkvTranscode);
        assertEquals("avi mpg mpeg mp4 m4v mkv mov wmv ogv divx m2ts", mkvTranscode.getSourceFormats());
        assertEquals("ffmpeg -ss %o -i %s -c:v libx264 -preset superfast -b:v %bk -c:a libvorbis -f matroska -threads 0 -", mkvTranscode.getStep1());
        assertNull(mkvTranscode.getStep2());
        assertNull(mkvTranscode.getStep3());
        assertEquals("mkv", mkvTranscode.getTargetFormat());

        //mp4
        Transcoding mp4Transcode = transcodings.stream().filter(t -> t.getName().equals("mp4/h264 video")).findFirst().get();
        assertNotNull(mp4Transcode);
        assertEquals("avi flv mpg mpeg m4v mkv mov wmv ogv divx m2ts", mp4Transcode.getSourceFormats());
        assertEquals("ffmpeg -ss %o -i %s -async 1 -b %bk -s %wx%h -ar 44100 -ac 2 -v 0 -f mp4 -vcodec libx264 -preset superfast -threads 0 -movflags frag_keyframe+empty_moov -", mp4Transcode.getStep1());
        assertNull(mp4Transcode.getStep2());
        assertNull(mp4Transcode.getStep3());
        assertEquals("mp4", mp4Transcode.getTargetFormat());

        //mod
        Transcoding modTranscode = transcodings.stream().filter(t -> t.getName().equals("mod > mp3")).findFirst().get();
        assertNotNull(modTranscode);
        assertEquals("alm 669 mdl far xm mod fnk imf it liq wow mtm ptm rtm stm s3m ult dmf dbm med okt emod sfx m15 mtn amf gdm stx gmc psm j2b umx amd rad hsc flx gtk mgt mtp", modTranscode.getSourceFormats());
        assertEquals("xmp -Dlittle-endian -q -c %s", modTranscode.getStep1());
        assertEquals("lame -r -b %b -S --resample 44.1 - -", modTranscode.getStep2());
        assertNull(modTranscode.getStep3());
        assertEquals("mp3", modTranscode.getTargetFormat());
    }

    @Test
    public void createTranscodingTest() {
        // Given
        Transcoding transcoding = new Transcoding(null,
                "test-transcoding",
                "mp3",
                "wav",
                "step1",
                "step2",
                "step3",
                true);

        transcodingService.createTranscoding(transcoding);
        verify(playerRepository).findAll();
        verify(transcodingRepository).save(transcoding);
        verify(playerRepository).saveAll(any());
    }
}
