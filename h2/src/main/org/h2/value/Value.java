/*
 * Copyright 2004-2020 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.value;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.lang.ref.SoftReference;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import org.h2.api.ErrorCode;
import org.h2.api.IntervalQualifier;
import org.h2.engine.CastDataProvider;
import org.h2.engine.SysProperties;
import org.h2.message.DbException;
import org.h2.result.ResultInterface;
import org.h2.result.SimpleResult;
import org.h2.store.DataHandler;
import org.h2.util.Bits;
import org.h2.util.DateTimeUtils;
import org.h2.util.HasSQL;
import org.h2.util.IntervalUtils;
import org.h2.util.JdbcUtils;
import org.h2.util.StringUtils;
import org.h2.util.geometry.GeoJsonUtils;

/**
 * This is the base class for all value classes.
 * It provides conversion and comparison methods.
 *
 * @author Thomas Mueller
 * @author Noel Grandin
 * @author Nicolas Fortin, Atelier SIG, IRSTV FR CNRS 24888
 */
public abstract class Value extends VersionedValue<Value> implements HasSQL {

    /**
     * The data type is unknown at this time.
     */
    public static final int UNKNOWN = -1;

    /**
     * The value type for NULL.
     */
    public static final int NULL = 0;

    /**
     * The value type for BOOLEAN values.
     */
    public static final int BOOLEAN = 1;

    /**
     * The value type for TINYINT values.
     */
    public static final int TINYINT = 2;

    /**
     * The value type for SMALLINT values.
     */
    public static final int SMALLINT = 3;

    /**
     * The value type for INTEGER values.
     */
    public static final int INT = 4;

    /**
     * The value type for BIGINT values.
     */
    public static final int BIGINT = 5;

    /**
     * The value type for NUMERIC values.
     */
    public static final int NUMERIC = 6;

    /**
     * The value type for DOUBLE PRECISION values.
     */
    public static final int DOUBLE = 7;

    /**
     * The value type for REAL values.
     */
    public static final int REAL = 8;

    /**
     * The value type for TIME values.
     */
    public static final int TIME = 9;

    /**
     * The value type for DATE values.
     */
    public static final int DATE = 10;

    /**
     * The value type for TIMESTAMP values.
     */
    public static final int TIMESTAMP = 11;

    /**
     * The value type for BINARY VARYING values.
     */
    public static final int VARBINARY = 12;

    /**
     * The value type for CHARACTER VARYING values.
     */
    public static final int VARCHAR = 13;

    /**
     * The value type for VARCHAR_IGNORECASE values.
     */
    public static final int VARCHAR_IGNORECASE = 14;

    /**
     * The value type for BLOB values.
     */
    public static final int BLOB = 15;

    /**
     * The value type for CLOB values.
     */
    public static final int CLOB = 16;

    /**
     * The value type for ARRAY values.
     */
    public static final int ARRAY = 17;

    /**
     * The value type for RESULT_SET values.
     */
    public static final int RESULT_SET = 18;

    /**
     * The value type for JAVA_OBJECT values.
     */
    public static final int JAVA_OBJECT = 19;

    /**
     * The value type for UUID values.
     */
    public static final int UUID = 20;

    /**
     * The value type for CHAR values.
     */
    public static final int CHAR = 21;

    /**
     * The value type for string values with a fixed size.
     */
    public static final int GEOMETRY = 22;

    /*
     * 23 was a short-lived experiment "TIMESTAMP UTC" which has been removed.
     */

    /**
     * The value type for TIMESTAMP WITH TIME ZONE values.
     */
    public static final int TIMESTAMP_TZ = 24;

    /**
     * The value type for ENUM values.
     */
    public static final int ENUM = 25;

    /**
     * The value type for {@code INTERVAL YEAR} values.
     */
    public static final int INTERVAL_YEAR = 26;

    /**
     * The value type for {@code INTERVAL MONTH} values.
     */
    public static final int INTERVAL_MONTH = 27;

    /**
     * The value type for {@code INTERVAL DAY} values.
     */
    public static final int INTERVAL_DAY = 28;

    /**
     * The value type for {@code INTERVAL HOUR} values.
     */
    public static final int INTERVAL_HOUR = 29;

    /**
     * The value type for {@code INTERVAL MINUTE} values.
     */
    public static final int INTERVAL_MINUTE = 30;

    /**
     * The value type for {@code INTERVAL SECOND} values.
     */
    public static final int INTERVAL_SECOND = 31;

    /**
     * The value type for {@code INTERVAL YEAR TO MONTH} values.
     */
    public static final int INTERVAL_YEAR_TO_MONTH = 32;

    /**
     * The value type for {@code INTERVAL DAY TO HOUR} values.
     */
    public static final int INTERVAL_DAY_TO_HOUR = 33;

    /**
     * The value type for {@code INTERVAL DAY TO MINUTE} values.
     */
    public static final int INTERVAL_DAY_TO_MINUTE = 34;

    /**
     * The value type for {@code INTERVAL DAY TO SECOND} values.
     */
    public static final int INTERVAL_DAY_TO_SECOND = 35;

    /**
     * The value type for {@code INTERVAL HOUR TO MINUTE} values.
     */
    public static final int INTERVAL_HOUR_TO_MINUTE = 36;

    /**
     * The value type for {@code INTERVAL HOUR TO SECOND} values.
     */
    public static final int INTERVAL_HOUR_TO_SECOND = 37;

    /**
     * The value type for {@code INTERVAL MINUTE TO SECOND} values.
     */
    public static final int INTERVAL_MINUTE_TO_SECOND = 38;

    /**
     * The value type for ROW values.
     */
    public static final int ROW = 39;

    /**
     * The value type for JSON values.
     */
    public static final int JSON = 40;

    /**
     * The value type for TIME WITH TIME ZONE values.
     */
    public static final int TIME_TZ = 41;

    /**
     * The number of value types.
     */
    public static final int TYPE_COUNT = TIME_TZ + 1;

    /**
     * Empty array of values.
     */
    public static final Value[] EMPTY_VALUES = new Value[0];

    private static SoftReference<Value[]> softCache;

    private static final BigDecimal MAX_LONG_DECIMAL = BigDecimal.valueOf(Long.MAX_VALUE);

    /**
     * The smallest Long value, as a BigDecimal.
     */
    public static final BigDecimal MIN_LONG_DECIMAL = BigDecimal.valueOf(Long.MIN_VALUE);

    /**
     * Check the range of the parameters.
     *
     * @param zeroBasedOffset the offset (0 meaning no offset)
     * @param length the length of the target
     * @param dataSize the length of the source
     */
    static void rangeCheck(long zeroBasedOffset, long length, long dataSize) {
        if ((zeroBasedOffset | length) < 0 || length > dataSize - zeroBasedOffset) {
            if (zeroBasedOffset < 0 || zeroBasedOffset > dataSize) {
                throw DbException.getInvalidValueException("offset", zeroBasedOffset + 1);
            }
            throw DbException.getInvalidValueException("length", length);
        }
    }

    /**
     * Returns the data type.
     *
     * @return the data type
     */
    public abstract TypeInfo getType();

    /**
     * Get the value type.
     *
     * @return the value type
     */
    public abstract int getValueType();

    /**
     * Get the memory used by this object.
     *
     * @return the memory used in bytes
     */
    public int getMemory() {
        /*
         * Java 11 with -XX:-UseCompressedOops for all values up to ValueLong
         * and ValueDouble.
         */
        return 24;
    }

    /**
     * Get the value as a string.
     *
     * @return the string
     */
    public abstract String getString();

    /**
     * Get the value as an object.
     *
     * @return the object
     */
    public abstract Object getObject();

    /**
     * Set the value as a parameter in a prepared statement.
     *
     * @param prep the prepared statement
     * @param parameterIndex the parameter index
     */
    public void set(PreparedStatement prep, int parameterIndex) throws SQLException {
        throw new UnsupportedOperationException();
    }

    @Override
    public abstract int hashCode();

    /**
     * Check if the two values have the same hash code. No data conversion is
     * made; this method returns false if the other object is not of the same
     * class. For some values, compareTo may return 0 even if equals return
     * false. Example: ValueDecimal 0.0 and 0.00.
     *
     * @param other the other value
     * @return true if they are equal
     */
    @Override
    public abstract boolean equals(Object other);

    /**
     * Get the order of this value type.
     *
     * @param type the value type
     * @return the order number
     */
    static int getOrder(int type) {
        switch (type) {
        case UNKNOWN:
            return 1_000;
        case NULL:
            return 2_000;
        case VARCHAR:
            return 10_000;
        case CLOB:
            return 11_000;
        case CHAR:
            return 12_000;
        case VARCHAR_IGNORECASE:
            return 13_000;
        case BOOLEAN:
            return 20_000;
        case TINYINT:
            return 21_000;
        case SMALLINT:
            return 22_000;
        case INT:
            return 23_000;
        case BIGINT:
            return 24_000;
        case NUMERIC:
            return 25_000;
        case REAL:
            return 26_000;
        case DOUBLE:
            return 27_000;
        case INTERVAL_YEAR:
            return 28_000;
        case INTERVAL_MONTH:
            return 28_100;
        case INTERVAL_YEAR_TO_MONTH:
            return 28_200;
        case INTERVAL_DAY:
            return 29_000;
        case INTERVAL_HOUR:
            return 29_100;
        case INTERVAL_DAY_TO_HOUR:
            return 29_200;
        case INTERVAL_MINUTE:
            return 29_300;
        case INTERVAL_HOUR_TO_MINUTE:
            return 29_400;
        case INTERVAL_DAY_TO_MINUTE:
            return 29_500;
        case INTERVAL_SECOND:
            return 29_600;
        case INTERVAL_MINUTE_TO_SECOND:
            return 29_700;
        case INTERVAL_HOUR_TO_SECOND:
            return 29_800;
        case INTERVAL_DAY_TO_SECOND:
            return 29_900;
        case TIME:
            return 30_000;
        case TIME_TZ:
            return 30_500;
        case DATE:
            return 31_000;
        case TIMESTAMP:
            return 32_000;
        case TIMESTAMP_TZ:
            return 34_000;
        case VARBINARY:
            return 40_000;
        case BLOB:
            return 41_000;
        case JAVA_OBJECT:
            return 42_000;
        case UUID:
            return 43_000;
        case GEOMETRY:
            return 44_000;
        case ENUM:
            return 45_000;
        case JSON:
            return 46_000;
        case ARRAY:
            return 50_000;
        case ROW:
            return 51_000;
        case RESULT_SET:
            return 52_000;
        default:
            throw DbException.throwInternalError("type:"+type);
        }
    }

    /**
     * Get the higher value order type of two value types. If values need to be
     * converted to match the other operands value type, the value with the
     * lower order is converted to the value with the higher order.
     *
     * @param t1 the first value type
     * @param t2 the second value type
     * @return the higher value type of the two
     */
    public static int getHigherOrder(int t1, int t2) {
        if (t1 == UNKNOWN || t2 == UNKNOWN) {
            if (t1 == t2) {
                throw DbException.get(ErrorCode.UNKNOWN_DATA_TYPE_1, "?, ?");
            } else if (t1 == NULL) {
                throw DbException.get(ErrorCode.UNKNOWN_DATA_TYPE_1, "NULL, ?");
            } else if (t2 == NULL) {
                throw DbException.get(ErrorCode.UNKNOWN_DATA_TYPE_1, "?, NULL");
            }
        }
        if (t1 == t2) {
            return t1;
        }
        int o1 = getOrder(t1);
        int o2 = getOrder(t2);
        return o1 > o2 ? t1 : t2;
    }

    /**
     * Get the higher data type of two data types. If values need to be
     * converted to match the other operands data type, the value with the
     * lower order is converted to the value with the higher order.
     *
     * @param type1 the first data type
     * @param type2 the second data type
     * @return the higher data type of the two
     */
    public static TypeInfo getHigherType(TypeInfo type1, TypeInfo type2) {
        int t1 = type1.getValueType(), t2 = type2.getValueType();
        int dataType = getHigherOrder(t1, t2);
        long precision = Math.max(type1.getPrecision(), type2.getPrecision());
        int scale = Math.max(type1.getScale(), type2.getScale());
        ExtTypeInfo ext1 = type1.getExtTypeInfo();
        ExtTypeInfo ext = dataType == t1 && ext1 != null ? ext1 : dataType == t2 ? type2.getExtTypeInfo() : null;
        return TypeInfo.getTypeInfo(dataType, precision, scale, ext);
    }

    /**
     * Check if a value is in the cache that is equal to this value. If yes,
     * this value should be used to save memory. If the value is not in the
     * cache yet, it is added.
     *
     * @param v the value to look for
     * @return the value in the cache or the value passed
     */
    static Value cache(Value v) {
        if (SysProperties.OBJECT_CACHE) {
            int hash = v.hashCode();
            Value[] cache;
            if (softCache == null || (cache = softCache.get()) == null) {
                cache = new Value[SysProperties.OBJECT_CACHE_SIZE];
                softCache = new SoftReference<>(cache);
            }
            int index = hash & (SysProperties.OBJECT_CACHE_SIZE - 1);
            Value cached = cache[index];
            if (cached != null) {
                if (cached.getValueType() == v.getValueType() && v.equals(cached)) {
                    // cacheHit++;
                    return cached;
                }
            }
            // cacheMiss++;
            // cache[cacheCleaner] = null;
            // cacheCleaner = (cacheCleaner + 1) &
            //     (Constants.OBJECT_CACHE_SIZE - 1);
            cache[index] = v;
        }
        return v;
    }

    /**
     * Clear the value cache. Used for testing.
     */
    public static void clearCache() {
        softCache = null;
    }

    public boolean getBoolean() {
        return convertTo(BOOLEAN).getBoolean();
    }

    public byte[] getBytes() {
        return convertTo(VARBINARY).getBytes();
    }

    public byte[] getBytesNoCopy() {
        return convertTo(VARBINARY).getBytesNoCopy();
    }

    public byte getByte() {
        return convertTo(TINYINT).getByte();
    }

    public short getShort() {
        return convertTo(SMALLINT).getShort();
    }

    public BigDecimal getBigDecimal() {
        return convertTo(NUMERIC).getBigDecimal();
    }

    public double getDouble() {
        return convertTo(DOUBLE).getDouble();
    }

    public float getFloat() {
        return convertTo(REAL).getFloat();
    }

    public int getInt() {
        return convertTo(INT).getInt();
    }

    public long getLong() {
        return convertTo(BIGINT).getLong();
    }

    public InputStream getInputStream() {
        return new ByteArrayInputStream(getBytesNoCopy());
    }

    /**
     * Get the input stream
     *
     * @param oneBasedOffset the offset (1 means no offset)
     * @param length the requested length
     * @return the new input stream
     */
    public InputStream getInputStream(long oneBasedOffset, long length) {
        byte[] bytes = getBytesNoCopy();
        long zeroBasedOffset = oneBasedOffset - 1;
        rangeCheck(zeroBasedOffset, length, bytes.length);
        return new ByteArrayInputStream(bytes, (int) zeroBasedOffset, (int) length);
    }

    public Reader getReader() {
        return new StringReader(getString());
    }

    /**
     * Get the reader
     *
     * @param oneBasedOffset the offset (1 means no offset)
     * @param length the requested length
     * @return the new reader
     */
    public Reader getReader(long oneBasedOffset, long length) {
        String string = getString();
        long zeroBasedOffset = oneBasedOffset - 1;
        rangeCheck(zeroBasedOffset, length, string.length());
        int offset = (int) zeroBasedOffset;
        return new StringReader(string.substring(offset, offset + (int) length));
    }

    /**
     * Add a value and return the result.
     *
     * @param v the value to add
     * @return the result
     */
    public Value add(@SuppressWarnings("unused") Value v) {
        throw getUnsupportedExceptionForOperation("+");
    }

    public int getSignum() {
        throw getUnsupportedExceptionForOperation("SIGNUM");
    }

    /**
     * Return -value if this value support arithmetic operations.
     *
     * @return the negative
     */
    public Value negate() {
        throw getUnsupportedExceptionForOperation("NEG");
    }

    /**
     * Subtract a value and return the result.
     *
     * @param v the value to subtract
     * @return the result
     */
    public Value subtract(@SuppressWarnings("unused") Value v) {
        throw getUnsupportedExceptionForOperation("-");
    }

    /**
     * Divide by a value and return the result.
     *
     * @param v the divisor
     * @param divisorPrecision the precision of divisor
     * @return the result
     */
    public Value divide(@SuppressWarnings("unused") Value v, long divisorPrecision) {
        throw getUnsupportedExceptionForOperation("/");
    }

    /**
     * Multiply with a value and return the result.
     *
     * @param v the value to multiply with
     * @return the result
     */
    public Value multiply(@SuppressWarnings("unused") Value v) {
        throw getUnsupportedExceptionForOperation("*");
    }

    /**
     * Take the modulus with a value and return the result.
     *
     * @param v the value to take the modulus with
     * @return the result
     */
    public Value modulus(@SuppressWarnings("unused") Value v) {
        throw getUnsupportedExceptionForOperation("%");
    }

    /**
     * Compare a value to the specified type.
     *
     * @param targetType the type of the returned value
     * @return the converted value
     */
    public final Value convertTo(int targetType) {
        return convertTo(targetType, null, null, null);
    }

    /**
     * Convert a value to the specified type.
     *
     * @param targetType the type of the returned value
     * @param provider the cast information provider
     * @return the converted value
     */
    public final Value convertTo(int targetType, CastDataProvider provider) {
        return convertTo(targetType, null, provider, null);
    }

    /**
     * Convert a value to the specified type.
     *
     * @param targetType the type of the returned value
     * @param provider the cast information provider
     * @param column the column (if any), used for to improve the error message if conversion fails
     * @return the converted value
     */
    public final Value convertTo(TypeInfo targetType, CastDataProvider provider, Object column) {
        return convertTo(targetType.getValueType(), targetType.getExtTypeInfo(), provider, column);
    }

    /**
     * Convert a value to the specified type.
     *
     * @param targetType the type of the returned value
     * @param extTypeInfo the extended data type information, or null
     * @param provider the cast information provider
     * @param column the column (if any), used for to improve the error message if conversion fails
     * @return the converted value
     */
    private Value convertTo(int targetType, ExtTypeInfo extTypeInfo, CastDataProvider provider, Object column) {
        int valueType = getValueType();
        if (valueType == NULL) {
            return this;
        }
        if (valueType == targetType) {
            if (extTypeInfo != null) {
                return extTypeInfo.cast(this, provider);
            }
            return this;
        }
        try {
            switch (targetType) {
            case NULL:
                return ValueNull.INSTANCE;
            case BOOLEAN:
                return convertToBoolean();
            case TINYINT:
                return convertToByte(column);
            case SMALLINT:
                return convertToShort(column);
            case INT:
                return convertToInt(column);
            case BIGINT:
                return convertToLong(column);
            case NUMERIC:
                return convertToDecimal();
            case DOUBLE:
                return convertToDouble();
            case REAL:
                return convertToFloat();
            case DATE:
                return convertToDate(provider);
            case TIME:
                return convertToTime(provider);
            case TIME_TZ:
                return convertToTimeTimeZone(provider);
            case TIMESTAMP:
                return convertToTimestamp(provider);
            case TIMESTAMP_TZ:
                return convertToTimestampTimeZone(provider);
            case VARBINARY:
                return convertToBytes();
            case VARCHAR:
                return ValueString.get(getString());
            case VARCHAR_IGNORECASE:
                return ValueStringIgnoreCase.get(getString());
            case CHAR:
                return ValueStringFixed.get(getString());
            case JAVA_OBJECT:
                return convertToJavaObject();
            case ENUM:
                return convertToEnumInternal((ExtTypeInfoEnum) extTypeInfo);
            case BLOB:
                return convertToBlob();
            case CLOB:
                return convertToClob();
            case UUID:
                return convertToUuid();
            case GEOMETRY:
                return convertToGeometry((ExtTypeInfoGeometry) extTypeInfo);
            case INTERVAL_YEAR:
            case INTERVAL_MONTH:
            case INTERVAL_YEAR_TO_MONTH:
                return convertToIntervalYearMonth(targetType, column);
            case INTERVAL_DAY:
            case INTERVAL_HOUR:
            case INTERVAL_MINUTE:
            case INTERVAL_SECOND:
            case INTERVAL_DAY_TO_HOUR:
            case INTERVAL_DAY_TO_MINUTE:
            case INTERVAL_DAY_TO_SECOND:
            case INTERVAL_HOUR_TO_MINUTE:
            case INTERVAL_HOUR_TO_SECOND:
            case INTERVAL_MINUTE_TO_SECOND:
                return convertToIntervalDayTime(targetType, column);
            case JSON:
                return convertToJson();
            case ARRAY:
                return convertToArray();
            case ROW:
                return convertToRow();
            case RESULT_SET:
                return convertToResultSet();
            default:
                throw getDataConversionError(targetType);
            }
        } catch (NumberFormatException e) {
            throw DbException.get(ErrorCode.DATA_CONVERSION_ERROR_1, e, getString());
        }
    }

    private ValueBoolean convertToBoolean() {
        switch (getValueType()) {
        case TINYINT:
        case SMALLINT:
        case INT:
        case BIGINT:
        case NUMERIC:
        case DOUBLE:
        case REAL:
            return ValueBoolean.get(getSignum() != 0);
        case TIME:
        case DATE:
        case TIMESTAMP:
        case TIMESTAMP_TZ:
        case VARBINARY:
        case JAVA_OBJECT:
        case UUID:
        case ENUM:
            throw getDataConversionError(BOOLEAN);
        }
        String s = getString();
        if (s.equalsIgnoreCase("true") || s.equalsIgnoreCase("t") || s.equalsIgnoreCase("yes")
                || s.equalsIgnoreCase("y")) {
            return ValueBoolean.TRUE;
        } else if (s.equalsIgnoreCase("false") || s.equalsIgnoreCase("f") || s.equalsIgnoreCase("no")
                || s.equalsIgnoreCase("n")) {
            return ValueBoolean.FALSE;
        } else {
            // convert to a number, and if it is not 0 then it is true
            return ValueBoolean.get(new BigDecimal(s).signum() != 0);
        }
    }

    private ValueByte convertToByte(Object column) {
        switch (getValueType()) {
        case BOOLEAN:
            return ValueByte.get(getBoolean() ? (byte) 1 : (byte) 0);
        case SMALLINT:
        case ENUM:
        case INT:
            return ValueByte.get(convertToByte(getInt(), column));
        case BIGINT:
        case INTERVAL_YEAR:
        case INTERVAL_MONTH:
        case INTERVAL_DAY:
        case INTERVAL_HOUR:
        case INTERVAL_MINUTE:
        case INTERVAL_SECOND:
        case INTERVAL_YEAR_TO_MONTH:
        case INTERVAL_DAY_TO_HOUR:
        case INTERVAL_DAY_TO_MINUTE:
        case INTERVAL_DAY_TO_SECOND:
        case INTERVAL_HOUR_TO_MINUTE:
        case INTERVAL_HOUR_TO_SECOND:
        case INTERVAL_MINUTE_TO_SECOND:
            return ValueByte.get(convertToByte(getLong(), column));
        case NUMERIC:
            return ValueByte.get(convertToByte(convertToLong(getBigDecimal(), column), column));
        case REAL:
        case DOUBLE:
            return ValueByte.get(convertToByte(convertToLong(getDouble(), column), column));
        case VARBINARY: {
            byte[] bytes = getBytesNoCopy();
            if (bytes.length == 1) {
                return ValueByte.get(bytes[0]);
            }
        }
        //$FALL-THROUGH$
        case TIMESTAMP_TZ:
            throw getDataConversionError(TINYINT);
        }
        return ValueByte.get(Byte.parseByte(getString().trim()));
    }

    private ValueShort convertToShort(Object column) {
        switch (getValueType()) {
        case BOOLEAN:
            return ValueShort.get(getBoolean() ? (short) 1 : (short) 0);
        case TINYINT:
            return ValueShort.get(getByte());
        case ENUM:
        case INT:
            return ValueShort.get(convertToShort(getInt(), column));
        case BIGINT:
        case INTERVAL_YEAR:
        case INTERVAL_MONTH:
        case INTERVAL_DAY:
        case INTERVAL_HOUR:
        case INTERVAL_MINUTE:
        case INTERVAL_SECOND:
        case INTERVAL_YEAR_TO_MONTH:
        case INTERVAL_DAY_TO_HOUR:
        case INTERVAL_DAY_TO_MINUTE:
        case INTERVAL_DAY_TO_SECOND:
        case INTERVAL_HOUR_TO_MINUTE:
        case INTERVAL_HOUR_TO_SECOND:
        case INTERVAL_MINUTE_TO_SECOND:
            return ValueShort.get(convertToShort(getLong(), column));
        case NUMERIC:
            return ValueShort.get(convertToShort(convertToLong(getBigDecimal(), column), column));
        case REAL:
        case DOUBLE:
            return ValueShort.get(convertToShort(convertToLong(getDouble(), column), column));
        case VARBINARY: {
            byte[] bytes = getBytesNoCopy();
            if (bytes.length == 2) {
                return ValueShort.get((short) ((bytes[0] << 8) + (bytes[1] & 0xff)));
            }
        }
        //$FALL-THROUGH$
        case TIMESTAMP_TZ:
            throw getDataConversionError(SMALLINT);
        }
        return ValueShort.get(Short.parseShort(getString().trim()));
    }

    private ValueInt convertToInt(Object column) {
        switch (getValueType()) {
        case BOOLEAN:
            return ValueInt.get(getBoolean() ? 1 : 0);
        case TINYINT:
        case ENUM:
        case SMALLINT:
            return ValueInt.get(getInt());
        case BIGINT:
        case INTERVAL_YEAR:
        case INTERVAL_MONTH:
        case INTERVAL_DAY:
        case INTERVAL_HOUR:
        case INTERVAL_MINUTE:
        case INTERVAL_SECOND:
        case INTERVAL_YEAR_TO_MONTH:
        case INTERVAL_DAY_TO_HOUR:
        case INTERVAL_DAY_TO_MINUTE:
        case INTERVAL_DAY_TO_SECOND:
        case INTERVAL_HOUR_TO_MINUTE:
        case INTERVAL_HOUR_TO_SECOND:
        case INTERVAL_MINUTE_TO_SECOND:
            return ValueInt.get(convertToInt(getLong(), column));
        case NUMERIC:
            return ValueInt.get(convertToInt(convertToLong(getBigDecimal(), column), column));
        case REAL:
        case DOUBLE:
            return ValueInt.get(convertToInt(convertToLong(getDouble(), column), column));
        case VARBINARY: {
            byte[] bytes = getBytesNoCopy();
            if (bytes.length == 4) {
                return ValueInt.get(Bits.readInt(bytes, 0));
            }
        }
        //$FALL-THROUGH$
        case TIMESTAMP_TZ:
            throw getDataConversionError(INT);
        }
        return ValueInt.get(Integer.parseInt(getString().trim()));
    }

    private ValueLong convertToLong(Object column) {
        switch (getValueType()) {
        case BOOLEAN:
            return ValueLong.get(getBoolean() ? 1 : 0);
        case TINYINT:
        case SMALLINT:
        case ENUM:
        case INT:
        case INTERVAL_YEAR:
        case INTERVAL_MONTH:
        case INTERVAL_DAY:
        case INTERVAL_HOUR:
        case INTERVAL_MINUTE:
        case INTERVAL_SECOND:
        case INTERVAL_YEAR_TO_MONTH:
        case INTERVAL_DAY_TO_HOUR:
        case INTERVAL_DAY_TO_MINUTE:
        case INTERVAL_DAY_TO_SECOND:
        case INTERVAL_HOUR_TO_MINUTE:
        case INTERVAL_HOUR_TO_SECOND:
        case INTERVAL_MINUTE_TO_SECOND:
            return ValueLong.get(getInt());
        case NUMERIC:
            return ValueLong.get(convertToLong(getBigDecimal(), column));
        case REAL:
        case DOUBLE:
            return ValueLong.get(convertToLong(getDouble(), column));
        case VARBINARY: {
            byte[] bytes = getBytesNoCopy();
            if (bytes.length == 8) {
                return ValueLong.get(Bits.readLong(bytes, 0));
            }
        }
        //$FALL-THROUGH$
        case TIMESTAMP_TZ:
            throw getDataConversionError(BIGINT);
        }
        return ValueLong.get(Long.parseLong(getString().trim()));
    }

    private ValueDecimal convertToDecimal() {
        switch (getValueType()) {
        case BOOLEAN:
            return getBoolean() ? ValueDecimal.ONE : ValueDecimal.ZERO;
        case TINYINT:
        case SMALLINT:
        case ENUM:
        case INT:
            return ValueDecimal.get(BigDecimal.valueOf(getInt()));
        case BIGINT:
            return ValueDecimal.get(BigDecimal.valueOf(getLong()));
        case DOUBLE:
        case REAL:
        case INTERVAL_YEAR:
        case INTERVAL_MONTH:
        case INTERVAL_DAY:
        case INTERVAL_HOUR:
        case INTERVAL_MINUTE:
        case INTERVAL_SECOND:
        case INTERVAL_YEAR_TO_MONTH:
        case INTERVAL_DAY_TO_HOUR:
        case INTERVAL_DAY_TO_MINUTE:
        case INTERVAL_DAY_TO_SECOND:
        case INTERVAL_HOUR_TO_MINUTE:
        case INTERVAL_HOUR_TO_SECOND:
        case INTERVAL_MINUTE_TO_SECOND:
            return ValueDecimal.get(getBigDecimal());
        case TIMESTAMP_TZ:
            throw getDataConversionError(NUMERIC);
        }
        return ValueDecimal.get(new BigDecimal(getString().trim()));
    }

    private ValueDouble convertToDouble() {
        switch (getValueType()) {
        case BOOLEAN:
            return getBoolean() ? ValueDouble.ONE : ValueDouble.ZERO;
        case TINYINT:
        case SMALLINT:
        case INT:
            return ValueDouble.get(getInt());
        case BIGINT:
        case INTERVAL_YEAR:
        case INTERVAL_MONTH:
        case INTERVAL_DAY:
        case INTERVAL_HOUR:
        case INTERVAL_MINUTE:
            return ValueDouble.get(getLong());
        case NUMERIC:
        case INTERVAL_SECOND:
        case INTERVAL_YEAR_TO_MONTH:
        case INTERVAL_DAY_TO_HOUR:
        case INTERVAL_DAY_TO_MINUTE:
        case INTERVAL_DAY_TO_SECOND:
        case INTERVAL_HOUR_TO_MINUTE:
        case INTERVAL_HOUR_TO_SECOND:
        case INTERVAL_MINUTE_TO_SECOND:
            return ValueDouble.get(getBigDecimal().doubleValue());
        case REAL:
            return ValueDouble.get(getFloat());
        case ENUM:
        case TIMESTAMP_TZ:
            throw getDataConversionError(DOUBLE);
        }
        return ValueDouble.get(Double.parseDouble(getString().trim()));
    }

    private ValueFloat convertToFloat() {
        switch (getValueType()) {
        case BOOLEAN:
            return getBoolean() ? ValueFloat.ONE : ValueFloat.ZERO;
        case TINYINT:
        case SMALLINT:
        case INT:
            return ValueFloat.get(getInt());
        case BIGINT:
        case INTERVAL_YEAR:
        case INTERVAL_MONTH:
        case INTERVAL_DAY:
        case INTERVAL_HOUR:
        case INTERVAL_MINUTE:
            return ValueFloat.get(getLong());
        case NUMERIC:
        case INTERVAL_SECOND:
        case INTERVAL_YEAR_TO_MONTH:
        case INTERVAL_DAY_TO_HOUR:
        case INTERVAL_DAY_TO_MINUTE:
        case INTERVAL_DAY_TO_SECOND:
        case INTERVAL_HOUR_TO_MINUTE:
        case INTERVAL_HOUR_TO_SECOND:
        case INTERVAL_MINUTE_TO_SECOND:
            return ValueFloat.get(getBigDecimal().floatValue());
        case DOUBLE:
            return ValueFloat.get((float) getDouble());
        case ENUM:
        case TIMESTAMP_TZ:
            throw getDataConversionError(REAL);
        }
        return ValueFloat.get(Float.parseFloat(getString().trim()));
    }

    private ValueDate convertToDate(CastDataProvider provider) {
        switch (getValueType()) {
        case TIMESTAMP:
            return ValueDate.fromDateValue(((ValueTimestamp) this).getDateValue());
        case TIMESTAMP_TZ: {
            ValueTimestampTimeZone ts = (ValueTimestampTimeZone) this;
            long timeNanos = ts.getTimeNanos();
            long epochSeconds = DateTimeUtils.getEpochSeconds(ts.getDateValue(), timeNanos,
                    ts.getTimeZoneOffsetSeconds());
            return ValueDate.fromDateValue(DateTimeUtils
                    .dateValueFromLocalSeconds(epochSeconds
                            + provider.currentTimeZone().getTimeZoneOffsetUTC(epochSeconds)));
        }
        case TIME:
        case TIME_TZ:
        case ENUM:
            throw getDataConversionError(DATE);
        }
        return ValueDate.parse(getString().trim());
    }

    private ValueTime convertToTime(CastDataProvider provider) {
        switch (getValueType()) {
        case TIME_TZ:
            return ValueTime.fromNanos(getLocalTimeNanos(provider));
        case TIMESTAMP:
            return ValueTime.fromNanos(((ValueTimestamp) this).getTimeNanos());
        case TIMESTAMP_TZ: {
            ValueTimestampTimeZone ts = (ValueTimestampTimeZone) this;
            long timeNanos = ts.getTimeNanos();
            long epochSeconds = DateTimeUtils.getEpochSeconds(ts.getDateValue(), timeNanos,
                    ts.getTimeZoneOffsetSeconds());
            return ValueTime.fromNanos(
                    DateTimeUtils.nanosFromLocalSeconds(epochSeconds
                            + provider.currentTimeZone().getTimeZoneOffsetUTC(epochSeconds))
                            + timeNanos % DateTimeUtils.NANOS_PER_SECOND);
        }
        case DATE:
        case ENUM:
            throw getDataConversionError(TIME);
        }
        return ValueTime.parse(getString().trim());
    }

    private ValueTimeTimeZone convertToTimeTimeZone(CastDataProvider provider) {
        switch (getValueType()) {
        case TIME: {
            return ValueTimeTimeZone.fromNanos(((ValueTime) this).getNanos(),
                    provider.currentTimestamp().getTimeZoneOffsetSeconds());
        }
        case TIMESTAMP: {
            ValueTimestamp ts = (ValueTimestamp) this;
            long timeNanos = ts.getTimeNanos();
            return ValueTimeTimeZone.fromNanos(timeNanos,
                    provider.currentTimeZone().getTimeZoneOffsetLocal(ts.getDateValue(), timeNanos));
        }
        case TIMESTAMP_TZ: {
            ValueTimestampTimeZone ts = (ValueTimestampTimeZone) this;
            return ValueTimeTimeZone.fromNanos(ts.getTimeNanos(), ts.getTimeZoneOffsetSeconds());
        }
        case DATE:
        case ENUM:
            throw getDataConversionError(TIME_TZ);
        }
        return ValueTimeTimeZone.parse(getString().trim());
    }

    private ValueTimestamp convertToTimestamp(CastDataProvider provider) {
        switch (getValueType()) {
        case TIME:
            return ValueTimestamp.fromDateValueAndNanos(provider.currentTimestamp().getDateValue(),
                    ((ValueTime) this).getNanos());
        case TIME_TZ:
            return ValueTimestamp.fromDateValueAndNanos(provider.currentTimestamp().getDateValue(),
                    getLocalTimeNanos(provider));
        case DATE:
            return ValueTimestamp.fromDateValueAndNanos(((ValueDate) this).getDateValue(), 0);
        case TIMESTAMP_TZ: {
            ValueTimestampTimeZone ts = (ValueTimestampTimeZone) this;
            long timeNanos = ts.getTimeNanos();
            long epochSeconds = DateTimeUtils.getEpochSeconds(ts.getDateValue(), timeNanos,
                    ts.getTimeZoneOffsetSeconds());
            epochSeconds += provider.currentTimeZone().getTimeZoneOffsetUTC(epochSeconds);
            return ValueTimestamp.fromDateValueAndNanos(DateTimeUtils.dateValueFromLocalSeconds(epochSeconds),
                    DateTimeUtils.nanosFromLocalSeconds(epochSeconds) + timeNanos % DateTimeUtils.NANOS_PER_SECOND);
        }
        case ENUM:
            throw getDataConversionError(TIMESTAMP);
        }
        return ValueTimestamp.parse(getString().trim(), provider);
    }

    private long getLocalTimeNanos(CastDataProvider provider) {
        ValueTimeTimeZone ts = (ValueTimeTimeZone) this;
        int localOffset = provider.currentTimestamp().getTimeZoneOffsetSeconds();
        return DateTimeUtils.normalizeNanosOfDay(ts.getNanos() +
                (ts.getTimeZoneOffsetSeconds() - localOffset) * DateTimeUtils.NANOS_PER_DAY);
    }

    private ValueTimestampTimeZone convertToTimestampTimeZone(CastDataProvider provider) {
        long dateValue, timeNanos;
        switch (getValueType()) {
        case TIME:
            dateValue = provider.currentTimestamp().getDateValue();
            timeNanos = ((ValueTime) this).getNanos();
            break;
        case TIME_TZ: {
            ValueTimeTimeZone t = (ValueTimeTimeZone) this;
            return ValueTimestampTimeZone.fromDateValueAndNanos(provider.currentTimestamp().getDateValue(),
                    t.getNanos(), t.getTimeZoneOffsetSeconds());
        }
        case DATE:
            dateValue = ((ValueDate) this).getDateValue();
            timeNanos = 0L;
            break;
        case TIMESTAMP: {
            ValueTimestamp ts = (ValueTimestamp) this;
            dateValue = ts.getDateValue();
            timeNanos = ts.getTimeNanos();
            break;
        }
        case ENUM:
            throw getDataConversionError(TIMESTAMP_TZ);
        default:
            return ValueTimestampTimeZone.parse(getString().trim(), provider);
        }
        return ValueTimestampTimeZone.fromDateValueAndNanos(dateValue, timeNanos,
                provider.currentTimeZone().getTimeZoneOffsetLocal(dateValue, timeNanos));
    }

    private ValueBytes convertToBytes() {
        switch (getValueType()) {
        case JAVA_OBJECT:
        case BLOB:
        case GEOMETRY:
        case JSON:
            return ValueBytes.getNoCopy(getBytesNoCopy());
        case UUID:
            return ValueBytes.getNoCopy(getBytes());
        case TINYINT:
            return ValueBytes.getNoCopy(new byte[] { getByte() });
        case SMALLINT: {
            int x = getShort();
            return ValueBytes.getNoCopy(new byte[] { (byte) (x >> 8), (byte) x });
        }
        case INT: {
            byte[] b = new byte[4];
            Bits.writeInt(b, 0, getInt());
            return ValueBytes.getNoCopy(b);
        }
        case BIGINT: {
            byte[] b = new byte[8];
            Bits.writeLong(b, 0, getLong());
            return ValueBytes.getNoCopy(b);
        }
        case ENUM:
        case TIMESTAMP_TZ:
            throw getDataConversionError(VARBINARY);
        }
        return ValueBytes.getNoCopy(getString().getBytes(StandardCharsets.UTF_8));
    }

    private ValueJavaObject convertToJavaObject() {
        switch (getValueType()) {
        case VARBINARY:
        case BLOB:
            return ValueJavaObject.getNoCopy(null, getBytesNoCopy(), getDataHandler());
        case GEOMETRY:
            return ValueJavaObject.getNoCopy(getObject(), null, getDataHandler());
        case ENUM:
        case TIMESTAMP_TZ:
            throw getDataConversionError(JAVA_OBJECT);
        }
        return ValueJavaObject.getNoCopy(null, StringUtils.convertHexToBytes(getString().trim()), getDataHandler());
    }

    private ValueEnum convertToEnumInternal(ExtTypeInfoEnum extTypeInfo) {
        switch (getValueType()) {
        case TINYINT:
        case SMALLINT:
        case INT:
        case BIGINT:
        case NUMERIC:
            return extTypeInfo.getValue(getInt());
        case VARCHAR:
        case VARCHAR_IGNORECASE:
        case CHAR:
            return extTypeInfo.getValue(getString());
        case JAVA_OBJECT:
            Object object = JdbcUtils.deserialize(getBytesNoCopy(), getDataHandler());
            if (object instanceof String) {
                return extTypeInfo.getValue((String) object);
            } else if (object instanceof Integer) {
                return extTypeInfo.getValue((int) object);
            }
            //$FALL-THROUGH$
        }
        throw getDataConversionError(ENUM);
    }

    protected Value convertToBlob() {
        switch (getValueType()) {
        case VARBINARY:
        case GEOMETRY:
        case JSON:
            return ValueLob.createSmallLob(BLOB, getBytesNoCopy());
        case UUID:
            return ValueLob.createSmallLob(BLOB, getBytes());
        case TIMESTAMP_TZ:
            throw getDataConversionError(BLOB);
        }
        return ValueLob.createSmallLob(BLOB, getString().getBytes(StandardCharsets.UTF_8));
    }

    protected Value convertToClob() {
        return ValueLob.createSmallLob(CLOB, getString().getBytes(StandardCharsets.UTF_8));
    }

    private ValueUuid convertToUuid() {
        switch (getValueType()) {
        case VARBINARY:
            return ValueUuid.get(getBytesNoCopy());
        case JAVA_OBJECT:
            Object object = JdbcUtils.deserialize(getBytesNoCopy(), getDataHandler());
            if (object instanceof java.util.UUID) {
                return ValueUuid.get((java.util.UUID) object);
            }
            //$FALL-THROUGH$
        case TIMESTAMP_TZ:
            throw getDataConversionError(UUID);
        }
        return ValueUuid.get(getString());
    }

    private Value convertToGeometry(ExtTypeInfoGeometry extTypeInfo) {
        ValueGeometry result;
        switch (getValueType()) {
        case VARBINARY:
            result = ValueGeometry.getFromEWKB(getBytesNoCopy());
            break;
        case JAVA_OBJECT:
            Object object = JdbcUtils.deserialize(getBytesNoCopy(), getDataHandler());
            if (DataType.isGeometry(object)) {
                result = ValueGeometry.getFromGeometry(object);
                break;
            }
            //$FALL-THROUGH$
        case TIMESTAMP_TZ:
            throw getDataConversionError(GEOMETRY);
        case JSON: {
            int srid = 0;
            if (extTypeInfo != null) {
                Integer s = extTypeInfo.getSrid();
                if (s != null) {
                    srid = s;
                }
            }
            try {
                result = ValueGeometry.get(GeoJsonUtils.geoJsonToEwkb(getBytesNoCopy(), srid));
            } catch (RuntimeException ex) {
                throw DbException.get(ErrorCode.DATA_CONVERSION_ERROR_1, getTraceSQL());
            }
            break;
        }
        default:
            result = ValueGeometry.get(getString());
        }
        return extTypeInfo != null ? extTypeInfo.cast(result, null) : result;
    }

    private ValueInterval convertToIntervalYearMonth(int targetType, Object column) {
        long leading;
        switch (getValueType()) {
        case TINYINT:
        case SMALLINT:
        case INT:
            leading = getInt();
            break;
        case BIGINT:
            leading = getLong();
            break;
        case REAL:
        case DOUBLE:
            if (targetType == INTERVAL_YEAR_TO_MONTH) {
                return IntervalUtils.intervalFromAbsolute(IntervalQualifier.YEAR_TO_MONTH, getBigDecimal()
                        .multiply(BigDecimal.valueOf(12)).setScale(0, RoundingMode.HALF_UP).toBigInteger());
            }
            leading = convertToLong(getDouble(), column);
            break;
        case NUMERIC:
            if (targetType == INTERVAL_YEAR_TO_MONTH) {
                return IntervalUtils.intervalFromAbsolute(IntervalQualifier.YEAR_TO_MONTH, getBigDecimal()
                        .multiply(BigDecimal.valueOf(12)).setScale(0, RoundingMode.HALF_UP).toBigInteger());
            }
            leading = convertToLong(getBigDecimal(), column);
            break;
        case VARCHAR:
        case VARCHAR_IGNORECASE:
        case CHAR: {
            String s = getString();
            try {
                return (ValueInterval) IntervalUtils
                        .parseFormattedInterval(IntervalQualifier.valueOf(targetType - INTERVAL_YEAR), s)
                        .convertTo(targetType);
            } catch (Exception e) {
                throw DbException.get(ErrorCode.INVALID_DATETIME_CONSTANT_2, e, "INTERVAL", s);
            }
        }
        case INTERVAL_YEAR:
        case INTERVAL_MONTH:
        case INTERVAL_YEAR_TO_MONTH:
            return IntervalUtils.intervalFromAbsolute(IntervalQualifier.valueOf(targetType - INTERVAL_YEAR),
                    IntervalUtils.intervalToAbsolute((ValueInterval) this));
        default:
            throw getDataConversionError(targetType);
        }
        boolean negative = false;
        if (leading < 0) {
            negative = true;
            leading = -leading;
        }
        return ValueInterval.from(IntervalQualifier.valueOf(targetType - INTERVAL_YEAR), negative, leading,
                0L);
    }

    private ValueInterval convertToIntervalDayTime(int targetType, Object column) {
        long leading;
        switch (getValueType()) {
        case TINYINT:
        case SMALLINT:
        case INT:
            leading = getInt();
            break;
        case BIGINT:
            leading = getLong();
            break;
        case REAL:
        case DOUBLE:
            if (targetType > INTERVAL_MINUTE) {
                return convertToIntervalDayTime(getBigDecimal(), targetType);
            }
            leading = convertToLong(getDouble(), column);
            break;
        case NUMERIC:
            if (targetType > INTERVAL_MINUTE) {
                return convertToIntervalDayTime(getBigDecimal(), targetType);
            }
            leading = convertToLong(getBigDecimal(), column);
            break;
        case VARCHAR:
        case VARCHAR_IGNORECASE:
        case CHAR: {
            String s = getString();
            try {
                return (ValueInterval) IntervalUtils
                        .parseFormattedInterval(IntervalQualifier.valueOf(targetType - INTERVAL_YEAR), s)
                        .convertTo(targetType);
            } catch (Exception e) {
                throw DbException.get(ErrorCode.INVALID_DATETIME_CONSTANT_2, e, "INTERVAL", s);
            }
        }
        case INTERVAL_DAY:
        case INTERVAL_HOUR:
        case INTERVAL_MINUTE:
        case INTERVAL_SECOND:
        case INTERVAL_DAY_TO_HOUR:
        case INTERVAL_DAY_TO_MINUTE:
        case INTERVAL_DAY_TO_SECOND:
        case INTERVAL_HOUR_TO_MINUTE:
        case INTERVAL_HOUR_TO_SECOND:
        case INTERVAL_MINUTE_TO_SECOND:
            return IntervalUtils.intervalFromAbsolute(IntervalQualifier.valueOf(targetType - INTERVAL_YEAR),
                    IntervalUtils.intervalToAbsolute((ValueInterval) this));
        default:
            throw getDataConversionError(targetType);
        }
        boolean negative = false;
        if (leading < 0) {
            negative = true;
            leading = -leading;
        }
        return ValueInterval.from(IntervalQualifier.valueOf(targetType - INTERVAL_YEAR), negative, leading,
                0L);
    }

    private ValueInterval convertToIntervalDayTime(BigDecimal bigDecimal, int targetType) {
        long multiplier;
        switch (targetType) {
        case INTERVAL_SECOND:
            multiplier = DateTimeUtils.NANOS_PER_SECOND;
            break;
        case INTERVAL_DAY_TO_HOUR:
        case INTERVAL_DAY_TO_MINUTE:
        case INTERVAL_DAY_TO_SECOND:
            multiplier = DateTimeUtils.NANOS_PER_DAY;
            break;
        case INTERVAL_HOUR_TO_MINUTE:
        case INTERVAL_HOUR_TO_SECOND:
            multiplier = DateTimeUtils.NANOS_PER_HOUR;
            break;
        case INTERVAL_MINUTE_TO_SECOND:
            multiplier = DateTimeUtils.NANOS_PER_MINUTE;
            break;
        default:
            throw getDataConversionError(targetType);
        }
        return IntervalUtils.intervalFromAbsolute(IntervalQualifier.valueOf(targetType - INTERVAL_YEAR),
                bigDecimal.multiply(BigDecimal.valueOf(multiplier)).setScale(0, RoundingMode.HALF_UP).toBigInteger());
    }

    private ValueJson convertToJson() {
        switch (getValueType()) {
        case BOOLEAN:
            return ValueJson.get(getBoolean());
        case TINYINT:
        case SMALLINT:
        case INT:
            return ValueJson.get(getInt());
        case BIGINT:
            return ValueJson.get(getLong());
        case REAL:
        case DOUBLE:
        case NUMERIC:
            return ValueJson.get(getBigDecimal());
        case VARBINARY:
        case BLOB:
            return ValueJson.fromJson(getBytesNoCopy());
        case VARCHAR:
        case VARCHAR_IGNORECASE:
        case CHAR:
        case CLOB:
            return ValueJson.get(getString());
        case GEOMETRY: {
            ValueGeometry vg = (ValueGeometry) this;
            return ValueJson.getInternal(GeoJsonUtils.ewkbToGeoJson(vg.getBytesNoCopy(), vg.getDimensionSystem()));
        }
        default:
            throw getDataConversionError(JSON);
        }
    }

    private ValueArray convertToArray() {
        Value[] a;
        switch (getValueType()) {
        case ROW:
            a = ((ValueRow) this).getList();
            break;
        case BLOB:
        case CLOB:
        case RESULT_SET:
            a = new Value[] { ValueString.get(getString()) };
            break;
        default:
            a = new Value[] { this };
        }
        return ValueArray.get(a);
    }

    private Value convertToRow() {
        Value[] a;
        if (getValueType() == RESULT_SET) {
            ResultInterface result = getResult();
            if (result.hasNext()) {
                a = result.currentRow();
                if (result.hasNext()) {
                    throw DbException.get(ErrorCode.SCALAR_SUBQUERY_CONTAINS_MORE_THAN_ONE_ROW);
                }
            } else {
                return ValueNull.INSTANCE;
            }
        } else {
            a = new Value[] { this };
        }
        return ValueRow.get(a);
    }

    private ValueResultSet convertToResultSet() {
        SimpleResult result = new SimpleResult();
        if (getValueType() == ROW) {
            Value[] values = ((ValueRow) this).getList();
            for (int i = 0; i < values.length;) {
                Value v = values[i++];
                String columnName = "C" + i;
                result.addColumn(columnName, columnName, v.getType());
            }
            result.addRow(values);
        } else {
            result.addColumn("X", "X", getType());
            result.addRow(this);
        }
        return ValueResultSet.get(result);
    }

    /**
     * Creates new instance of the DbException for data conversion error.
     *
     * @param targetType Target data type.
     * @return instance of the DbException.
     */
    DbException getDataConversionError(int targetType) {
        DataType from = DataType.getDataType(getValueType());
        DataType to = DataType.getDataType(targetType);
        throw DbException.get(ErrorCode.DATA_CONVERSION_ERROR_1, (from != null ? from.name : "type=" + getValueType())
                + " to " + (to != null ? to.name : "type=" + targetType));
    }

    /**
     * Compare this value against another value given that the values are of the
     * same data type.
     *
     * @param v the other value
     * @param mode the compare mode
     * @param provider the cast information provider
     * @return 0 if both values are equal, -1 if the other value is smaller, and
     *         1 otherwise
     */
    public abstract int compareTypeSafe(Value v, CompareMode mode, CastDataProvider provider);

    /**
     * Compare this value against another value using the specified compare
     * mode.
     *
     * @param v the other value
     * @param provider the cast information provider
     * @param compareMode the compare mode
     * @return 0 if both values are equal, -1 if this value is smaller, and
     *         1 otherwise
     */
    public final int compareTo(Value v, CastDataProvider provider, CompareMode compareMode) {
        if (this == v) {
            return 0;
        }
        if (this == ValueNull.INSTANCE) {
            return -1;
        } else if (v == ValueNull.INSTANCE) {
            return 1;
        }
        return compareToNotNullable(v, provider, compareMode);
    }

    private int compareToNotNullable(Value v, CastDataProvider provider, CompareMode compareMode) {
        Value l = this;
        int leftType = l.getValueType();
        int rightType = v.getValueType();
        if (leftType != rightType || leftType == ENUM) {
            int dataType = getHigherOrder(leftType, rightType);
            if (dataType == ENUM) {
                ExtTypeInfoEnum enumerators = ExtTypeInfoEnum.getEnumeratorsForBinaryOperation(l, v);
                l = l.convertTo(ENUM, enumerators, null, null);
                v = v.convertTo(ENUM, enumerators, null, null);
            } else {
                l = l.convertTo(dataType, provider);
                v = v.convertTo(dataType, provider);
            }
        }
        return l.compareTypeSafe(v, compareMode, provider);
    }

    /**
     * Compare this value against another value using the specified compare
     * mode.
     *
     * @param v the other value
     * @param forEquality perform only check for equality
     * @param provider the cast information provider
     * @param compareMode the compare mode
     * @return 0 if both values are equal, -1 if this value is smaller, 1
     *         if other value is larger, {@link Integer#MIN_VALUE} if order is
     *         not defined due to NULL comparison
     */
    public int compareWithNull(Value v, boolean forEquality, CastDataProvider provider,
            CompareMode compareMode) {
        if (this == ValueNull.INSTANCE || v == ValueNull.INSTANCE) {
            return Integer.MIN_VALUE;
        }
        return compareToNotNullable(v, provider, compareMode);
    }

    /**
     * Returns true if this value is NULL or contains NULL value.
     *
     * @return true if this value is NULL or contains NULL value
     */
    public boolean containsNull() {
        return false;
    }

    /**
     * Convert the scale.
     *
     * @param onlyToSmallerScale if the scale should not reduced
     * @param targetScale the requested scale
     * @return the value
     */
    @SuppressWarnings("unused")
    public Value convertScale(boolean onlyToSmallerScale, int targetScale) {
        return this;
    }

    /**
     * Convert the precision to the requested value. The precision of the
     * returned value may be somewhat larger than requested, because values with
     * a fixed precision are not truncated.
     *
     * @param precision the new precision
     * @return the new value
     */
    @SuppressWarnings("unused")
    public Value convertPrecision(long precision) {
        return this;
    }

    private static byte convertToByte(long x, Object column) {
        if (x > Byte.MAX_VALUE || x < Byte.MIN_VALUE) {
            throw DbException.get(
                    ErrorCode.NUMERIC_VALUE_OUT_OF_RANGE_2, Long.toString(x), getColumnName(column));
        }
        return (byte) x;
    }

    private static short convertToShort(long x, Object column) {
        if (x > Short.MAX_VALUE || x < Short.MIN_VALUE) {
            throw DbException.get(
                    ErrorCode.NUMERIC_VALUE_OUT_OF_RANGE_2, Long.toString(x), getColumnName(column));
        }
        return (short) x;
    }

    /**
     * Convert to integer, throwing exception if out of range.
     *
     * @param x integer value.
     * @param column Column info.
     * @return x
     */
    public static int convertToInt(long x, Object column) {
        if (x > Integer.MAX_VALUE || x < Integer.MIN_VALUE) {
            throw DbException.get(
                    ErrorCode.NUMERIC_VALUE_OUT_OF_RANGE_2, Long.toString(x), getColumnName(column));
        }
        return (int) x;
    }

    private static long convertToLong(double x, Object column) {
        if (x > Long.MAX_VALUE || x < Long.MIN_VALUE) {
            // TODO document that +Infinity, -Infinity throw an exception and
            // NaN returns 0
            throw DbException.get(
                    ErrorCode.NUMERIC_VALUE_OUT_OF_RANGE_2, Double.toString(x), getColumnName(column));
        }
        return Math.round(x);
    }

    private static long convertToLong(BigDecimal x, Object column) {
        if (x.compareTo(MAX_LONG_DECIMAL) > 0 ||
                x.compareTo(MIN_LONG_DECIMAL) < 0) {
            throw DbException.get(
                    ErrorCode.NUMERIC_VALUE_OUT_OF_RANGE_2, x.toString(), getColumnName(column));
        }
        return x.setScale(0, RoundingMode.HALF_UP).longValue();
    }

    private static String getColumnName(Object column) {
        return column == null ? "" : column.toString();
    }

    /**
     * Check if the precision is smaller or equal than the given precision.
     *
     * @param precision the maximum precision
     * @return true if the precision of this value is smaller or equal to the
     *         given precision
     */
    public boolean checkPrecision(long precision) {
        return getType().getPrecision() <= precision;
    }

    @Override
    public String toString() {
        return getTraceSQL();
    }

    /**
     * Create an exception meaning the specified operation is not supported for
     * this data type.
     *
     * @param op the operation
     * @return the exception
     */
    protected final DbException getUnsupportedExceptionForOperation(String op) {
        return DbException.getUnsupportedException(
                DataType.getDataType(getValueType()).name + " " + op);
    }

    /**
     * Returns result for result set value, or single-row result with this value
     * in column X for other values.
     *
     * @return result
     */
    public ResultInterface getResult() {
        SimpleResult rs = new SimpleResult();
        rs.addColumn("X", "X", getType());
        rs.addRow(this);
        return rs;
    }

    /**
     * Return the data handler for the values that support it
     * (actually only Java objects).
     * @return the data handler
     */
    protected DataHandler getDataHandler() {
        return null;
    }

}
