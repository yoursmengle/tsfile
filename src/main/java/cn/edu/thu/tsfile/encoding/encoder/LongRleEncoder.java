package cn.edu.thu.tsfile.encoding.encoder;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;

import cn.edu.thu.tsfile.common.utils.ReadWriteStreamUtils;
import cn.edu.thu.tsfile.encoding.bitpacking.LongPacker;
import cn.edu.thu.tsfile.encoding.common.EndianType;

/**
 * @Description Encoder for long value using rle or bit-packing
 * @author XuYi xuyi556677@163.com
 * @date Mar 25, 2016
 */
public class LongRleEncoder extends RleEncoder<Long> {

    /**
     * Packer for packing long value
     */
    private LongPacker packer;

    public LongRleEncoder(EndianType endianType) {
	super(endianType);
	bufferedValues = new Long[config.RLE_MIN_REPEATED_NUM];
	preValue = (long) 0;
	values = new ArrayList<Long>();
    }

    @Override
    public void encode(long value, ByteArrayOutputStream out) {
	values.add(value);
    }

    /**
     * write all values buffered in cache to OutputStream
     * 
     * @param out
     *            - byteArrayOutputStream
     * @throws IOException
     */
    @Override
    public void flush(ByteArrayOutputStream out) throws IOException {
	// we get bit width after receiving all data
	this.bitWidth = ReadWriteStreamUtils.getLongMaxBitWidth(values);
	packer = new LongPacker(bitWidth);
	for (Long value : values) {
	    encodeValue(value);
	}
	super.flush(out);
    }

    @Override
    protected void reset() {
	super.reset();
	preValue = (long) 0;
    }

    /**
     * write bytes to OutputStream using rle rle format: [header][value]
     */
    @Override
    protected void writeRleRun() throws IOException {
	endPreviousBitPackedRun(config.RLE_MIN_REPEATED_NUM);
	ReadWriteStreamUtils.writeUnsignedVarInt(repeatCount << 1, byteCache);
	ReadWriteStreamUtils.writeLongLittleEndianPaddedOnBitWidth(preValue, byteCache, bitWidth);
	repeatCount = 0;
	numBufferedValues = 0;
    }

    @Override
    protected void clearBuffer() {
	for (int i = numBufferedValues; i < config.RLE_MIN_REPEATED_NUM; i++) {
	    bufferedValues[i] = (long) 0;
	}
    }

    @Override
    protected void convertBuffer() {
	byte[] bytes = new byte[bitWidth];
	long[] tmpBuffer = new long[config.RLE_MIN_REPEATED_NUM];
	for (int i = 0; i < config.RLE_MIN_REPEATED_NUM; i++) {
	    tmpBuffer[i] = (long) bufferedValues[i];
	}
	packer.pack8Values(tmpBuffer, 0, bytes);
	// we'll not write bit-packing group to OutputStream immediately
	// we buffer them in list
	bytesBuffer.add(bytes);
    }

    @Override
    public int getOneItemMaxSize() {
	// 4 + 4 + max(4+8,1 + 4 + 8 * 8)
	// length + bitwidth + max(rle-header + num, bit-header + lastNum + 8packer)
	return 77;
    }

    @Override
    public long getMaxByteSize() {
	if (values == null) {
	    return 0;
	}
	// try to caculate max value
	int groupNum = (values.size() / 8 + 1) / 63 + 1;
	return 8 + groupNum * 5 + values.size() * 8;
    }
}