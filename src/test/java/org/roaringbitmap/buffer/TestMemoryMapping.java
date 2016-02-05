/*
 * (c) the authors
 * Licensed under the Apache License, Version 2.0.
 */

package org.roaringbitmap.buffer;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.roaringbitmap.IntIterator;
import org.roaringbitmap.RoaringBitmap;

@SuppressWarnings({ "static-method" })
public class TestMemoryMapping {

    @Test
    public void multithreadingTest() throws InterruptedException, IOException {
        System.out.println("[TestMemoryMapping] multithreading test");
        displayMemory();
        final MutableRoaringBitmap rr1 = new MutableRoaringBitmap();

        final int numThreads = Runtime.getRuntime().availableProcessors();
        final Throwable[] errors = new Throwable[numThreads];

        for (int i = 0; i < numThreads; i++) {
            // each thread will check an integer from a different container
            rr1.add(Short.MAX_VALUE * i);
        }

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(bos);
        rr1.serialize(dos);
        dos.close();
        ByteBuffer bb = ByteBuffer.wrap(bos.toByteArray());
        final ImmutableRoaringBitmap rrback1 = new ImmutableRoaringBitmap(bb);

        final CountDownLatch ready = new CountDownLatch(numThreads);
        final CountDownLatch finished = new CountDownLatch(numThreads);

        ExecutorService executorService = Executors.newFixedThreadPool(numThreads);

        for (int i = 0; i < numThreads; i++) {
            final int ti = i;
            executorService.execute(new Runnable() {

                @Override
                public void run() {
                    ready.countDown();
                    try {
                        ready.await();
                        final int elementToCheck = Short.MAX_VALUE * ti;
                        for (int j = 0; j < 10000000; j++) {
                            try {
                                assertTrue(rrback1.contains(elementToCheck));
                            } catch (Throwable t) {
                                errors[ti] = t;
                            }
                        }
                    } catch (Throwable e) {
                        errors[ti] = e;
                    }
                    finished.countDown();
                }
            });
        }
        finished.await(5, TimeUnit.SECONDS);
        for (int i = 0; i < numThreads; i++) {
            if (errors[i] != null) {
                errors[i].printStackTrace();
                fail("The contains() for the element " + Short.MAX_VALUE * i + " throw an exception");
            }
        }
    }

    @Test
    public void standardTest() throws IOException {
        System.out.println("[TestMemoryMapping] standard test");
        displayMemory();
        MutableRoaringBitmap rr1 = MutableRoaringBitmap.bitmapOf(1, 2, 3, 1000);
        MutableRoaringBitmap rr2 = MutableRoaringBitmap.bitmapOf(2, 3, 1010);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(bos);
        rr1.serialize(dos);
        rr2.serialize(dos);
        dos.close();
        ByteBuffer bb = ByteBuffer.wrap(bos.toByteArray());
        ImmutableRoaringBitmap rrback1 = new ImmutableRoaringBitmap(bb);
        Assert.assertTrue(rr1.equals(rrback1));
        bb.position(bb.position() + rrback1.serializedSizeInBytes());
        ImmutableRoaringBitmap rrback2 = new ImmutableRoaringBitmap(bb);
        Assert.assertTrue(rr1.equals(rrback1));
        Assert.assertTrue(rr2.equals(rrback2));
    }

    @Test
    public void standardTest1() throws IOException {
        System.out.println("[TestMemoryMapping] standard test 1");
        displayMemory();
        // use some run containers
        MutableRoaringBitmap rr1 = MutableRoaringBitmap.bitmapOf(1, 2, 3, 4, 5, 6, 1000);
        rr1.runOptimize();
        MutableRoaringBitmap rr2 = MutableRoaringBitmap.bitmapOf(2, 3, 1010);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(bos);
        rr1.serialize(dos);
        rr2.serialize(dos);
        dos.close();
        ByteBuffer bb = ByteBuffer.wrap(bos.toByteArray());
        ImmutableRoaringBitmap rrback1 = new ImmutableRoaringBitmap(bb);
        Assert.assertTrue(rr1.equals(rrback1));
        bb.position(bb.position() + rrback1.serializedSizeInBytes());
        ImmutableRoaringBitmap rrback2 = new ImmutableRoaringBitmap(bb);
        Assert.assertTrue(rr1.equals(rrback1));
        Assert.assertTrue(rr2.equals(rrback2));
        ImmutableRoaringBitmap rrback1c = rrback1.clone();
        ImmutableRoaringBitmap rrback2c = rrback2.clone();
        Assert.assertTrue(rr1.equals(rrback1c));
        Assert.assertTrue(rr2.equals(rrback2c));
        Assert.assertTrue(rrback1.equals(rrback1c));
        Assert.assertTrue(rrback2.equals(rrback2c));
        Assert.assertEquals(rr1.hashCode(), rrback1.hashCode());
        Assert.assertEquals(rr1.hashCode(), rrback1c.hashCode());
        Assert.assertEquals(rr2.hashCode(), rrback2.hashCode());
        Assert.assertEquals(rr2.hashCode(), rrback2c.hashCode());

    }

    @Test
    public void basic() {
        System.out.println("[TestMemoryMapping] basic tests");
        displayMemory(); 
        for (int k = 0; k < mappedbitmaps.size(); ++k) {
            Assert.assertTrue(mappedbitmaps.get(k).equals(rambitmaps.get(k)));
        }
    }

    @Test
    public void testIterator() {
        System.out.println("[TestMemoryMapping] test iterators");
        displayMemory();
        final int ms = mappedbitmaps.size();
        for (int k = 0; k < ms; ++k) {
            System.out.println("[TestMemoryMapping] testing copy via iterators "+k+" out of "+ms);
            MutableRoaringBitmap copy0 = mappedbitmaps.get(k).toMutableRoaringBitmap();
            Assert.assertTrue(copy0.equals(mappedbitmaps.get(k)));
            MutableRoaringBitmap copy1 = new MutableRoaringBitmap();
            for (int x : mappedbitmaps.get(k)) {
                copy1.add(x);
            }
            Assert.assertTrue(copy1.equals(mappedbitmaps.get(k)));
            copy1 = null;
            MutableRoaringBitmap copy2 = new MutableRoaringBitmap();
            IntIterator i = mappedbitmaps.get(k).getIntIterator();
            while (i.hasNext()) {
                copy2.add(i.next());
            }
            Assert.assertTrue(copy2.equals(mappedbitmaps.get(k)));
            System.out.println("[TestMemoryMapping] testing copy via iterators "+k+" out of "+ms+" ok");
        }
        System.out.println("[TestMemoryMapping] testing a custom iterator copy  ");

        MutableRoaringBitmap rb = new MutableRoaringBitmap();
        for (int k = 0; k < 4000; ++k)
            rb.add(k);
        for (int k = 0; k < 1000; ++k)
            rb.add(k * 100);
        MutableRoaringBitmap copy1 = new MutableRoaringBitmap();
        for (int x : rb) {
            copy1.add(x);
        }
        Assert.assertTrue(copy1.equals(rb));
        MutableRoaringBitmap copy2 = new MutableRoaringBitmap();
        IntIterator i = rb.getIntIterator();
        while (i.hasNext()) {
            copy2.add(i.next());
        }
        Assert.assertTrue(copy2.equals(rb));
        System.out.println("[TestMemoryMapping] testing a custom iterator copy  ok");
    }

    @Test
    public void complements() {
        System.out.println("[TestMemoryMapping] testing complements");
        displayMemory();
        for (int k = 0; k < mappedbitmaps.size() - 1; k += 4) {
            final MutableRoaringBitmap rb = ImmutableRoaringBitmap.andNot(mappedbitmaps.get(k), mappedbitmaps.get(k + 1));
            final MutableRoaringBitmap rbram = ImmutableRoaringBitmap.andNot(rambitmaps.get(k), rambitmaps.get(k + 1));
            Assert.assertTrue(rb.equals(rbram));
        }
    }

    @Test
    public void intersections() {
        System.out.println("[TestMemoryMapping] testing intersections");
        displayMemory();
        for (int k = 0; k + 1 < mappedbitmaps.size(); k += 2) {
            final MutableRoaringBitmap rb = ImmutableRoaringBitmap.and(mappedbitmaps.get(k), mappedbitmaps.get(k + 1));
            final MutableRoaringBitmap rbram = ImmutableRoaringBitmap.and(rambitmaps.get(k), rambitmaps.get(k + 1));
            Assert.assertTrue(rb.equals(rbram));
        }

        for (int k = 0; k < mappedbitmaps.size() - 4; k += 4) {
            final MutableRoaringBitmap rb = BufferFastAggregation.and(mappedbitmaps.get(k), mappedbitmaps.get(k + 1), mappedbitmaps.get(k + 3),
                    mappedbitmaps.get(k + 4));
            final MutableRoaringBitmap rbram = BufferFastAggregation
                    .and(rambitmaps.get(k), rambitmaps.get(k + 1), rambitmaps.get(k + 3), rambitmaps.get(k + 4));
            Assert.assertTrue(rb.equals(rbram));
        }
    }

    @Test
    public void unions() {
        System.out.println("[TestMemoryMapping] testing Unions");
        displayMemory();
        for (int k = 0; k < mappedbitmaps.size() - 4; k += 4) {
            final MutableRoaringBitmap rb = BufferFastAggregation.or(mappedbitmaps.get(k), mappedbitmaps.get(k + 1), mappedbitmaps.get(k + 3),
                    mappedbitmaps.get(k + 4));
            final MutableRoaringBitmap rbram = BufferFastAggregation.or(rambitmaps.get(k), rambitmaps.get(k + 1), rambitmaps.get(k + 3), rambitmaps.get(k + 4));
            Assert.assertTrue(rb.equals(rbram));
        }
    }

    @Test
    public void XORs() {
        System.out.println("[TestMemoryMapping] testing XORs");
        displayMemory();
        for (int k = 0; k < mappedbitmaps.size() - 4; k += 4) {
            final MutableRoaringBitmap rb = BufferFastAggregation.xor(mappedbitmaps.get(k), mappedbitmaps.get(k + 1), mappedbitmaps.get(k + 3),
                    mappedbitmaps.get(k + 4));
            final MutableRoaringBitmap rbram = BufferFastAggregation
                    .xor(rambitmaps.get(k), rambitmaps.get(k + 1), rambitmaps.get(k + 3), rambitmaps.get(k + 4));
            Assert.assertTrue(rb.equals(rbram));
        }
    }

    @Test
    public void reserialize() throws IOException {
        System.out.println("[TestMemoryMapping] testing reserialization");
        displayMemory();
        for (int k = 0; k < mappedbitmaps.size(); ++k) {
            ImmutableRoaringBitmap rr = mappedbitmaps.get(k);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(bos);
            rr.serialize(dos);
            dos.close();
            ByteBuffer bb = ByteBuffer.wrap(bos.toByteArray());
            ImmutableRoaringBitmap rrback = new ImmutableRoaringBitmap(bb);
            Assert.assertTrue(rr.equals(rrback));
            Assert.assertTrue(rr.equals(rrback.toMutableRoaringBitmap()));
            Assert.assertTrue(rr.toMutableRoaringBitmap().equals(rrback));
            Assert.assertTrue(rr.toMutableRoaringBitmap().equals(rambitmaps.get(k)));
        }
    }
    
    public static void displayMemory() {
        System.out.println("[TestMemoryMapping] free memory: "+ Runtime.getRuntime().freeMemory()+" bytes");
        System.out.println("[TestMemoryMapping] total memory: "+ Runtime.getRuntime().totalMemory()+" bytes");
        System.out.println("[TestMemoryMapping] max memory: "+ Runtime.getRuntime().maxMemory()+" bytes");
    }

    @Test
    public void oneFormat() throws IOException {
        System.out.println("[TestMemoryMapping] testing format compatibility");
        final int ms = mappedbitmaps.size();
        displayMemory();
        for (int k = 0; k < ms; ++k) {
            System.out.println("[TestMemoryMapping] testing compat. bitmap "+k+" out of "+ms);
            ImmutableRoaringBitmap rr = mappedbitmaps.get(k);
            ByteArrayOutputStream bos = new ByteArrayOutputStream(rr.serializedSizeInBytes());
            DataOutputStream dos = new DataOutputStream(bos);
            rr.serialize(dos);
            dos.close();
            byte[] arr = bos.toByteArray();
            bos = null;
            ByteArrayInputStream bis = new ByteArrayInputStream(arr);
            RoaringBitmap newr = new RoaringBitmap();
            newr.deserialize(new DataInputStream(bis));
            arr = null;
            RoaringBitmap rrasroaring = rr.toRoaringBitmap();
            Assert.assertEquals(newr,rrasroaring);
            System.out.println("[TestMemoryMapping] testing compat. bitmap "+k+" out of "+ms+". ok.");
        }
        System.out.println("[TestMemoryMapping] Format compatibility ok");

    }

    @AfterClass
    public static void clearFiles() {
        System.out.println("[TestMemoryMapping] Cleaning memory-mapped file.");
        out = null;
        rambitmaps = null;
        mappedbitmaps = null;
        tmpfile.delete();
    }

    public static ByteBuffer toByteBuffer(RoaringBitmap rb) {
        // we add tests
        ByteBuffer outbb = ByteBuffer.allocate(rb.serializedSizeInBytes());
        try {
            rb.serialize(new DataOutputStream(new ByteBufferBackedOutputStream(outbb)));
        } catch (IOException e) {
            e.printStackTrace();
        }
        //
        outbb.flip();
        outbb.order(ByteOrder.LITTLE_ENDIAN);
        return outbb;
    }

    public static RoaringBitmap toRoaringBitmap(ByteBuffer bb) {
        RoaringBitmap rb = new RoaringBitmap();
        try {
            rb.deserialize(new DataInputStream(new ByteBufferBackedInputStream(bb)));
        } catch (IOException e) {
            e.printStackTrace();
        }
        return rb;
    }

    public static MutableRoaringBitmap toMutableRoaringBitmap(ByteBuffer bb) {
        MutableRoaringBitmap rb = new MutableRoaringBitmap();
        try {
            rb.deserialize(new DataInputStream(new ByteBufferBackedInputStream(bb)));
        } catch (IOException e) {
            e.printStackTrace();
        }
        return rb;
    }

    public static ByteBuffer toByteBuffer(MutableRoaringBitmap rb) {
        // we add tests
        ByteBuffer outbb = ByteBuffer.allocate(rb.serializedSizeInBytes());
        try {
            rb.serialize(new DataOutputStream(new ByteBufferBackedOutputStream(outbb)));
        } catch (IOException e) {
            e.printStackTrace();
        }
        //
        outbb.flip();
        outbb.order(ByteOrder.LITTLE_ENDIAN);
        return outbb;
    }

    public static boolean equals(ByteBuffer bb1, ByteBuffer bb2) {
        if (bb1.limit() != bb2.limit())
            return false;
        for (int k = 0; k < bb1.limit(); ++k) {
            if (bb1.get(k) != bb2.get(k))
                return false;
        }
        return true;
    }

    @BeforeClass
    public static void initFiles() throws IOException {
        System.out.println("[TestMemoryMapping] Setting up memory-mapped file. (Can take some time.)");
        displayMemory();
        final ArrayList<Long> offsets = new ArrayList<Long>();
        tmpfile = File.createTempFile("roaring", "bin");
        tmpfile.deleteOnExit();
        final FileOutputStream fos = new FileOutputStream(tmpfile);
        final DataOutputStream dos = new DataOutputStream(fos);
        for (int N = 65536 * 16; N <= 65536 * 128; N *= 8) {
            for (int gap = 1; gap <= 65536; gap *= 4) {
                final MutableRoaringBitmap rb1 = new MutableRoaringBitmap();
                for (int x = 0; x < N; x += gap) {
                    rb1.add(x);
                }
                // make containers 8 and 10 be run encoded

                for (int x = 8 * 65536; x < 8 * 65536 + 1000; ++x)
                    rb1.add(x);

                for (int x = 10 * 65536; x < 10 * 65536 + 1000; ++x) {
                    rb1.add(x);
                    rb1.add(10000 + x);
                }

                {
                    RoaringBitmap rr = RoaringBitmap.bitmapOf(rb1.toArray());
                    ByteBuffer bb = toByteBuffer(rb1);
                    if (!equals(toByteBuffer(rr), bb))
                        throw new RuntimeException("serialized output not identical.");
                    bb.rewind();
                    RoaringBitmap rr2 = toRoaringBitmap(bb);
                    if (!rr2.equals(rr))
                        throw new RuntimeException("Can't recover RoaringBitmap");
                    bb.rewind();
                    MutableRoaringBitmap rb2 = toMutableRoaringBitmap(bb);
                    if (!rb1.equals(rb2))
                        throw new RuntimeException("Can't recover MutableRoaringBitmap");
                    bb.rewind();
                    ImmutableRoaringBitmap irb = new ImmutableRoaringBitmap(bb);
                    if (!irb.equals(rb1))
                        throw new RuntimeException("serialized output cannot be mapped.");
                }

                rb1.runOptimize();

                {
                    RoaringBitmap rr = RoaringBitmap.bitmapOf(rb1.toArray());
                    rr.runOptimize();
                    ByteBuffer bb = toByteBuffer(rb1);
                    if (!equals(toByteBuffer(rr), bb))
                        throw new RuntimeException("serialized output not identical.");
                    bb.rewind();
                    RoaringBitmap rr2 = toRoaringBitmap(bb);
                    if (!rr2.equals(rr))
                        throw new RuntimeException("Can't recover RoaringBitmap");
                    bb.rewind();
                    MutableRoaringBitmap rb2 = toMutableRoaringBitmap(bb);
                    if (!rb1.equals(rb2))
                        throw new RuntimeException("Can't recover MutableRoaringBitmap");
                    bb.rewind();
                    ImmutableRoaringBitmap irb = new ImmutableRoaringBitmap(bb);
                    if (!irb.equals(rb1))
                        throw new RuntimeException("serialized output cannot be mapped.");
                }

                rambitmaps.add(rb1);
                offsets.add(fos.getChannel().position());
                rb1.serialize(dos);
                dos.flush();
                for (int offset = 1; offset <= gap; offset *= 8) {
                    final MutableRoaringBitmap rb2 = new MutableRoaringBitmap();
                    for (int x = 0; x < N; x += gap) {
                        rb2.add(x + offset);
                    }
                    // gap 1 gives runcontainers
                    rb2.runOptimize();
                    offsets.add(fos.getChannel().position());
                    long pbef = fos.getChannel().position();
                    rb2.serialize(dos);
                    long paft = fos.getChannel().position();
                    if (paft - pbef != rb2.serializedSizeInBytes()) {
                        throw new RuntimeException("wrong serializedSizeInBytes:: paft-pbef = " + (paft - pbef) + ", serializedSize = "
                                + rb2.serializedSizeInBytes());
                    }
                    dos.flush();
                    rambitmaps.add(rb2);
                    // we add tests
                    ByteBuffer outbb = ByteBuffer.allocate(rb2.serializedSizeInBytes());
                    rb2.serialize(new DataOutputStream(new ByteBufferBackedOutputStream(outbb)));
                    //
                    outbb.flip();
                    ImmutableRoaringBitmap irb = new ImmutableRoaringBitmap(outbb);
                    if (!irb.equals(rb2))
                        throw new RuntimeException("No hope of working");
                }
            }
        }
        final long totalcount = fos.getChannel().position();
        System.out.println("[TestMemoryMapping] Wrote " + totalcount / 1024 + " KB");
        offsets.add(totalcount);
        dos.close();
        final RandomAccessFile memoryMappedFile = new RandomAccessFile(tmpfile, "r");
        try {
            out = memoryMappedFile.getChannel().map(FileChannel.MapMode.READ_ONLY, 0, totalcount);
            final long bef = System.currentTimeMillis();
            for (int k = 0; k < offsets.size() - 1; ++k) {
                final ByteBuffer bb = out.slice();
                // Next commented line is not required nor recommended
                // bb.limit((int) (offsets.get(k+1)-offsets.get(k)));
                ImmutableRoaringBitmap newbitmap = new ImmutableRoaringBitmap(bb);
                if (newbitmap.serializedSizeInBytes() != rambitmaps.get(k).serializedSizeInBytes()) {
                    throw new RuntimeException("faulty reported serialization size " + newbitmap.serializedSizeInBytes() + " "
                            + rambitmaps.get(k).serializedSizeInBytes());
                }
                if (!newbitmap.equals(rambitmaps.get(k))) {
                    throw new RuntimeException("faulty serialization");
                }
                mappedbitmaps.add(newbitmap);
                out.position(out.position() + newbitmap.serializedSizeInBytes());
                if (out.position() != offsets.get(k + 1).longValue())
                    throw new RuntimeException("faulty serialization");
            }
            final long aft = System.currentTimeMillis();
            System.out.println("[TestMemoryMapping] Mapped " + (offsets.size() - 1) + " bitmaps in " + (aft - bef) + "ms");
        } finally {
            memoryMappedFile.close();
        }
    }

    static ArrayList<ImmutableRoaringBitmap> mappedbitmaps = new ArrayList<ImmutableRoaringBitmap>();

    static MappedByteBuffer out;

    static ArrayList<MutableRoaringBitmap> rambitmaps = new ArrayList<MutableRoaringBitmap>();

    static File tmpfile;
}

class ByteBufferBackedInputStream extends InputStream {

    ByteBuffer buf;

    ByteBufferBackedInputStream(ByteBuffer buf) {
        this.buf = buf;
    }
    
    @Override
    public int read() throws IOException {
        if (!buf.hasRemaining()) {
            return -1;
        }
        return 0xFF & buf.get();
    }

    @Override
    public int read(byte[] bytes) throws IOException {
        int len = Math.min(bytes.length, buf.remaining());
        buf.get(bytes, 0, len);
        return len;
    }
    
    @Override
    public long skip(long n) {
        int len = Math.min((int) n, buf.remaining());
        buf.position(buf.position() + (int) n);
        return len;
    }

    @Override
    public int available() throws IOException {
        return buf.remaining();
    }

    @Override
    public boolean markSupported() {
        return false;
    }

    @Override
    public int read(byte[] bytes, int off, int len) throws IOException {
        len = Math.min(len, buf.remaining());
        buf.get(bytes, off, len);
        return len;
    }
}

class ByteBufferBackedOutputStream extends OutputStream {
    ByteBuffer buf;

    ByteBufferBackedOutputStream(ByteBuffer buf) {
        this.buf = buf;
    }
    
    @Override
    public synchronized void write(int b) throws IOException {
        buf.put((byte) b);
    }
    
    @Override
    public synchronized void write(byte[] bytes) throws IOException {
        buf.put(bytes);
    }
    
    @Override
    public synchronized void write(byte[] bytes, int off, int len) throws IOException {
        buf.put(bytes, off, len);
    }

}
