package org.airsonic.player.io;

import org.airsonic.player.domain.MediaFile;
import org.airsonic.player.domain.PlayQueue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class PlayQueueInputStreamTest {

    @Mock
    private PlayQueue queue;

    @Mock
    private MediaFile file1;

    @Mock
    private MediaFile file2;

    @Test
    void testBytesAccumulatedAcrossReads() throws IOException {
        byte[] data = new byte[100];
        for (int i = 0; i < data.length; i++) data[i] = (byte) i;

        when(queue.getCurrentFile()).thenReturn(file1);
        when(queue.getStatus()).thenReturn(PlayQueue.Status.PLAYING);

        AtomicLong capturedBytes = new AtomicLong(-1);

        try (PlayQueueInputStream pqis = new PlayQueueInputStream(
                queue,
                f -> {},
                (bytes, f) -> capturedBytes.set(bytes),
                f -> new ByteArrayInputStream(data))) {

            byte[] buf = new byte[10];
            int total = 0;
            int n;
            // exhaust the stream then trigger closeStream via null file
            while ((n = pqis.read(buf)) > 0) {
                total += n;
                if (total >= 100) break;
            }
            when(queue.getCurrentFile()).thenReturn(null);
            pqis.read(buf); // triggers closeStream via prepare()
        }

        assertThat(capturedBytes.get()).isEqualTo(100L);
    }

    @Test
    void testFileEndListenerReceivesBytesNotCallCount() throws IOException {
        byte[] data = new byte[50];

        when(queue.getCurrentFile()).thenReturn(file1);
        when(queue.getStatus()).thenReturn(PlayQueue.Status.PLAYING);

        AtomicLong capturedBytes = new AtomicLong(-1);

        try (PlayQueueInputStream pqis = new PlayQueueInputStream(
                queue,
                f -> {},
                (bytes, f) -> capturedBytes.set(bytes),
                f -> new ByteArrayInputStream(data))) {

            // 10 reads of 5 bytes each
            byte[] buf = new byte[5];
            for (int i = 0; i < 10; i++) {
                pqis.read(buf);
            }
            when(queue.getCurrentFile()).thenReturn(null);
            pqis.read(buf); // flush via closeStream
        }

        // Must be 50 (bytes), not 10 (call count)
        assertThat(capturedBytes.get()).isEqualTo(50L);
    }

    @Test
    void testBytesResetBetweenFiles() throws IOException {
        byte[] data1 = new byte[30];
        byte[] data2 = new byte[70];

        List<Long> capturedBytes = new ArrayList<>();

        // First call returns file1, subsequent calls after next() return file2
        when(queue.getCurrentFile()).thenReturn(file1);
        when(queue.getStatus()).thenReturn(PlayQueue.Status.PLAYING);

        PlayQueueInputStream pqis = new PlayQueueInputStream(
                queue,
                f -> {},
                (bytes, f) -> capturedBytes.add(bytes),
                f -> {
                    if (f == file1) return new ByteArrayInputStream(data1);
                    return new ByteArrayInputStream(data2);
                });

        // Read file1 fully -- when ByteArrayInputStream returns -1, PlayQueueInputStream
        // calls queue.next() then closeStream()
        when(queue.getCurrentFile())
                .thenReturn(file1)   // prepare() for first read
                .thenReturn(file1)   // getStatus() calls inside read
                .thenReturn(file2);  // after next() returns file2

        byte[] buf = new byte[100];
        // First read gets file1's 30 bytes
        pqis.read(buf, 0, 100);
        // closeStream was called for file1 (ByteArrayInputStream exhausted), file2 starts
        // close the stream to flush file2's listener
        when(queue.getCurrentFile()).thenReturn(null);
        pqis.close();

        assertThat(capturedBytes).hasSize(2);
        assertThat(capturedBytes.get(0)).isEqualTo(30L); // file1
        assertThat(capturedBytes.get(1)).isEqualTo(70L); // file2
    }
}
