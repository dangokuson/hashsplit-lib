package org.hashsplit4j.api;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

/**
 * Puts files back together
 *
 * @author brad
 */
public class Combiner {

    private long currentByte = 0;
    private int currentFanout = 0;
    private int currentBlob = 0;
    private int currentBlobByte;
    private long bytesWritten;
    private boolean canceled;

    public void combine(List<String> fanoutHashes, HashStore hashStore, BlobStore blobStore, OutputStream out) throws IOException {
        if (canceled) {
            throw new IOException("Operation cancelled");
        }
        for (String fanoutHash : fanoutHashes) {
            Fanout fanout = hashStore.getChunkFanout(fanoutHash);
            for (String hash : fanout.getHashes()) {
                if (canceled) {
                    throw new IOException("Operation cancelled");
                }

                byte[] arr = blobStore.getBlob(hash);
                if (arr == null) {
                    throw new RuntimeException("Failed to lookup blob: " + hash);
                }
                out.write(arr);
                bytesWritten += arr.length;
            }
        }

    }

    public void combine(List<String> megaCrcs, HashStore hashStore, BlobStore blobStore, OutputStream out, long start, Long finish) throws IOException {
        seek(start, megaCrcs, hashStore, blobStore);
        writeToFinish(finish, megaCrcs, hashStore, blobStore, out);
    }

    private void seek(long start, List<String> megaCrcs, HashStore hashStore, BlobStore blobStore) throws IOException {
        while (currentFanout < megaCrcs.size()) {
            String fanoutHash = megaCrcs.get(currentFanout);
            Fanout fanout = hashStore.getChunkFanout(fanoutHash);
            long fanoutEnd = currentByte + fanout.getActualContentLength();
            if (fanoutEnd >= start) {
                while (currentBlob < fanout.getHashes().size()) {
                    if (canceled) {
                        throw new IOException("Operation cancelled");
                    }

                    String blobHash = fanout.getHashes().get(currentBlob);
                    byte[] arr = blobStore.getBlob(blobHash);
                    if (arr == null) {
                        throw new RuntimeException("Failed to find blob in fanout. Blob hash: " + blobHash);
                    }
                    if (currentByte + arr.length >= start) { // if end is after beginning of range, then this is the blob we want
                        currentBlobByte = (int) (start - currentByte);
                        currentByte += currentBlobByte;
                        return;
                    } else {
                        currentByte += arr.length;
                    }
                    currentBlob++;
                }
            } else {
                currentByte += fanout.getActualContentLength();
            }
            currentFanout++;
        }
    }

    private void writeToFinish(Long finish, List<String> megaCrcs, HashStore hashStore, BlobStore blobStore, OutputStream out) throws IOException {
        while (currentFanout < megaCrcs.size() && (finish == null || currentByte < finish)) {
            String fanoutHash = megaCrcs.get(currentFanout);
            Fanout fanout = hashStore.getChunkFanout(fanoutHash);
            while (currentBlob < fanout.getHashes().size() && (finish == null || currentByte < finish)) {
                if (canceled) {
                    throw new IOException("Operation cancelled");
                }

                String hash = fanout.getHashes().get(currentBlob);
                byte[] arr = blobStore.getBlob(hash);
                if (arr == null) {
                    throw new RuntimeException("Couldnt locate blob: " + hash);
                }
                int numBytes;
                if (finish == null || currentByte + arr.length < finish) {
                    // write all remaining bytes
                    numBytes = arr.length - currentBlobByte;
                } else {
                    numBytes = (int) (finish - currentByte + 1);
                }
                out.write(arr, currentBlobByte, numBytes);
                bytesWritten += numBytes;
                currentBlobByte = 0;
                currentByte += numBytes;
                currentBlob++;
            }
            currentFanout++;
            currentBlob = 0;
        }
    }

    public long getBytesWritten() {
        return bytesWritten;
    }

    public int getCurrentBlob() {
        return currentBlob;
    }

    public int getCurrentBlobByte() {
        return currentBlobByte;
    }

    public long getCurrentByte() {
        return currentByte;
    }

    public int getCurrentFanout() {
        return currentFanout;
    }

    public boolean isCanceled() {
        return canceled;
    }

    public void setCanceled(boolean canceled) {
        this.canceled = canceled;
    }
}
