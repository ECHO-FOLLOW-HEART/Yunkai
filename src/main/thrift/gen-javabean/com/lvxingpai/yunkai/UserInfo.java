/**
 * Autogenerated by Thrift Compiler (0.9.2)
 *
 * DO NOT EDIT UNLESS YOU ARE SURE THAT YOU KNOW WHAT YOU ARE DOING
 *  @generated
 */
package com.lvxingpai.yunkai;

import org.apache.thrift.scheme.IScheme;
import org.apache.thrift.scheme.SchemeFactory;
import org.apache.thrift.scheme.StandardScheme;

import org.apache.thrift.scheme.TupleScheme;
import org.apache.thrift.protocol.TTupleProtocol;
import org.apache.thrift.protocol.TProtocolException;
import org.apache.thrift.EncodingUtils;
import org.apache.thrift.TException;
import org.apache.thrift.async.AsyncMethodCallback;
import org.apache.thrift.server.AbstractNonblockingServer.*;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.EnumMap;
import java.util.Set;
import java.util.HashSet;
import java.util.EnumSet;
import java.util.Collections;
import java.util.BitSet;
import java.nio.ByteBuffer;
import java.util.Arrays;
import javax.annotation.Generated;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings({"cast", "rawtypes", "serial", "unchecked"})
@Generated(value = "Autogenerated by Thrift Compiler (0.9.2)", date = "2015-6-11")
public class UserInfo implements org.apache.thrift.TBase<UserInfo, UserInfo._Fields>, java.io.Serializable, Cloneable, Comparable<UserInfo> {
  private static final org.apache.thrift.protocol.TStruct STRUCT_DESC = new org.apache.thrift.protocol.TStruct("UserInfo");

  private static final org.apache.thrift.protocol.TField USER_ID_FIELD_DESC = new org.apache.thrift.protocol.TField("userId", org.apache.thrift.protocol.TType.I64, (short)1);
  private static final org.apache.thrift.protocol.TField NICK_NAME_FIELD_DESC = new org.apache.thrift.protocol.TField("nickName", org.apache.thrift.protocol.TType.STRING, (short)2);
  private static final org.apache.thrift.protocol.TField AVATAR_FIELD_DESC = new org.apache.thrift.protocol.TField("avatar", org.apache.thrift.protocol.TType.STRING, (short)3);
  private static final org.apache.thrift.protocol.TField GENDER_FIELD_DESC = new org.apache.thrift.protocol.TField("gender", org.apache.thrift.protocol.TType.I32, (short)4);
  private static final org.apache.thrift.protocol.TField SIGNATURE_FIELD_DESC = new org.apache.thrift.protocol.TField("signature", org.apache.thrift.protocol.TType.STRING, (short)5);
  private static final org.apache.thrift.protocol.TField TEL_FIELD_DESC = new org.apache.thrift.protocol.TField("tel", org.apache.thrift.protocol.TType.STRING, (short)6);

  private static final Map<Class<? extends IScheme>, SchemeFactory> schemes = new HashMap<Class<? extends IScheme>, SchemeFactory>();
  static {
    schemes.put(StandardScheme.class, new UserInfoStandardSchemeFactory());
    schemes.put(TupleScheme.class, new UserInfoTupleSchemeFactory());
  }

  private long userId; // required
  private String nickName; // required
  private String avatar; // optional
  private Gender gender; // optional
  private String signature; // optional
  private String tel; // optional

  /** The set of fields this struct contains, along with convenience methods for finding and manipulating them. */
  public enum _Fields implements org.apache.thrift.TFieldIdEnum {
    USER_ID((short)1, "userId"),
    NICK_NAME((short)2, "nickName"),
    AVATAR((short)3, "avatar"),
    /**
     * 
     * @see Gender
     */
    GENDER((short)4, "gender"),
    SIGNATURE((short)5, "signature"),
    TEL((short)6, "tel");

    private static final Map<String, _Fields> byName = new HashMap<String, _Fields>();

    static {
      for (_Fields field : EnumSet.allOf(_Fields.class)) {
        byName.put(field.getFieldName(), field);
      }
    }

    /**
     * Find the _Fields constant that matches fieldId, or null if its not found.
     */
    public static _Fields findByThriftId(int fieldId) {
      switch(fieldId) {
        case 1: // USER_ID
          return USER_ID;
        case 2: // NICK_NAME
          return NICK_NAME;
        case 3: // AVATAR
          return AVATAR;
        case 4: // GENDER
          return GENDER;
        case 5: // SIGNATURE
          return SIGNATURE;
        case 6: // TEL
          return TEL;
        default:
          return null;
      }
    }

    /**
     * Find the _Fields constant that matches fieldId, throwing an exception
     * if it is not found.
     */
    public static _Fields findByThriftIdOrThrow(int fieldId) {
      _Fields fields = findByThriftId(fieldId);
      if (fields == null) throw new IllegalArgumentException("Field " + fieldId + " doesn't exist!");
      return fields;
    }

    /**
     * Find the _Fields constant that matches name, or null if its not found.
     */
    public static _Fields findByName(String name) {
      return byName.get(name);
    }

    private final short _thriftId;
    private final String _fieldName;

    _Fields(short thriftId, String fieldName) {
      _thriftId = thriftId;
      _fieldName = fieldName;
    }

    public short getThriftFieldId() {
      return _thriftId;
    }

    public String getFieldName() {
      return _fieldName;
    }
  }

  // isset id assignments
  private static final int __USERID_ISSET_ID = 0;
  private byte __isset_bitfield = 0;
  private static final _Fields optionals[] = {_Fields.AVATAR,_Fields.GENDER,_Fields.SIGNATURE,_Fields.TEL};
  public static final Map<_Fields, org.apache.thrift.meta_data.FieldMetaData> metaDataMap;
  static {
    Map<_Fields, org.apache.thrift.meta_data.FieldMetaData> tmpMap = new EnumMap<_Fields, org.apache.thrift.meta_data.FieldMetaData>(_Fields.class);
    tmpMap.put(_Fields.USER_ID, new org.apache.thrift.meta_data.FieldMetaData("userId", org.apache.thrift.TFieldRequirementType.DEFAULT, 
        new org.apache.thrift.meta_data.FieldValueMetaData(org.apache.thrift.protocol.TType.I64)));
    tmpMap.put(_Fields.NICK_NAME, new org.apache.thrift.meta_data.FieldMetaData("nickName", org.apache.thrift.TFieldRequirementType.DEFAULT, 
        new org.apache.thrift.meta_data.FieldValueMetaData(org.apache.thrift.protocol.TType.STRING)));
    tmpMap.put(_Fields.AVATAR, new org.apache.thrift.meta_data.FieldMetaData("avatar", org.apache.thrift.TFieldRequirementType.OPTIONAL, 
        new org.apache.thrift.meta_data.FieldValueMetaData(org.apache.thrift.protocol.TType.STRING)));
    tmpMap.put(_Fields.GENDER, new org.apache.thrift.meta_data.FieldMetaData("gender", org.apache.thrift.TFieldRequirementType.OPTIONAL, 
        new org.apache.thrift.meta_data.EnumMetaData(org.apache.thrift.protocol.TType.ENUM, Gender.class)));
    tmpMap.put(_Fields.SIGNATURE, new org.apache.thrift.meta_data.FieldMetaData("signature", org.apache.thrift.TFieldRequirementType.OPTIONAL, 
        new org.apache.thrift.meta_data.FieldValueMetaData(org.apache.thrift.protocol.TType.STRING)));
    tmpMap.put(_Fields.TEL, new org.apache.thrift.meta_data.FieldMetaData("tel", org.apache.thrift.TFieldRequirementType.OPTIONAL, 
        new org.apache.thrift.meta_data.FieldValueMetaData(org.apache.thrift.protocol.TType.STRING)));
    metaDataMap = Collections.unmodifiableMap(tmpMap);
    org.apache.thrift.meta_data.FieldMetaData.addStructMetaDataMap(UserInfo.class, metaDataMap);
  }

  public UserInfo() {
  }

  public UserInfo(
    long userId,
    String nickName)
  {
    this();
    this.userId = userId;
    setUserIdIsSet(true);
    this.nickName = nickName;
  }

  /**
   * Performs a deep copy on <i>other</i>.
   */
  public UserInfo(UserInfo other) {
    __isset_bitfield = other.__isset_bitfield;
    this.userId = other.userId;
    if (other.isSetNickName()) {
      this.nickName = other.nickName;
    }
    if (other.isSetAvatar()) {
      this.avatar = other.avatar;
    }
    if (other.isSetGender()) {
      this.gender = other.gender;
    }
    if (other.isSetSignature()) {
      this.signature = other.signature;
    }
    if (other.isSetTel()) {
      this.tel = other.tel;
    }
  }

  public UserInfo deepCopy() {
    return new UserInfo(this);
  }

  @Override
  public void clear() {
    setUserIdIsSet(false);
    this.userId = 0;
    this.nickName = null;
    this.avatar = null;
    this.gender = null;
    this.signature = null;
    this.tel = null;
  }

  public long getUserId() {
    return this.userId;
  }

  public void setUserId(long userId) {
    this.userId = userId;
    setUserIdIsSet(true);
  }

  public void unsetUserId() {
    __isset_bitfield = EncodingUtils.clearBit(__isset_bitfield, __USERID_ISSET_ID);
  }

  /** Returns true if field userId is set (has been assigned a value) and false otherwise */
  public boolean isSetUserId() {
    return EncodingUtils.testBit(__isset_bitfield, __USERID_ISSET_ID);
  }

  public void setUserIdIsSet(boolean value) {
    __isset_bitfield = EncodingUtils.setBit(__isset_bitfield, __USERID_ISSET_ID, value);
  }

  public String getNickName() {
    return this.nickName;
  }

  public void setNickName(String nickName) {
    this.nickName = nickName;
  }

  public void unsetNickName() {
    this.nickName = null;
  }

  /** Returns true if field nickName is set (has been assigned a value) and false otherwise */
  public boolean isSetNickName() {
    return this.nickName != null;
  }

  public void setNickNameIsSet(boolean value) {
    if (!value) {
      this.nickName = null;
    }
  }

  public String getAvatar() {
    return this.avatar;
  }

  public void setAvatar(String avatar) {
    this.avatar = avatar;
  }

  public void unsetAvatar() {
    this.avatar = null;
  }

  /** Returns true if field avatar is set (has been assigned a value) and false otherwise */
  public boolean isSetAvatar() {
    return this.avatar != null;
  }

  public void setAvatarIsSet(boolean value) {
    if (!value) {
      this.avatar = null;
    }
  }

  /**
   * 
   * @see Gender
   */
  public Gender getGender() {
    return this.gender;
  }

  /**
   * 
   * @see Gender
   */
  public void setGender(Gender gender) {
    this.gender = gender;
  }

  public void unsetGender() {
    this.gender = null;
  }

  /** Returns true if field gender is set (has been assigned a value) and false otherwise */
  public boolean isSetGender() {
    return this.gender != null;
  }

  public void setGenderIsSet(boolean value) {
    if (!value) {
      this.gender = null;
    }
  }

  public String getSignature() {
    return this.signature;
  }

  public void setSignature(String signature) {
    this.signature = signature;
  }

  public void unsetSignature() {
    this.signature = null;
  }

  /** Returns true if field signature is set (has been assigned a value) and false otherwise */
  public boolean isSetSignature() {
    return this.signature != null;
  }

  public void setSignatureIsSet(boolean value) {
    if (!value) {
      this.signature = null;
    }
  }

  public String getTel() {
    return this.tel;
  }

  public void setTel(String tel) {
    this.tel = tel;
  }

  public void unsetTel() {
    this.tel = null;
  }

  /** Returns true if field tel is set (has been assigned a value) and false otherwise */
  public boolean isSetTel() {
    return this.tel != null;
  }

  public void setTelIsSet(boolean value) {
    if (!value) {
      this.tel = null;
    }
  }

  public void setFieldValue(_Fields field, Object value) {
    switch (field) {
    case USER_ID:
      if (value == null) {
        unsetUserId();
      } else {
        setUserId((Long)value);
      }
      break;

    case NICK_NAME:
      if (value == null) {
        unsetNickName();
      } else {
        setNickName((String)value);
      }
      break;

    case AVATAR:
      if (value == null) {
        unsetAvatar();
      } else {
        setAvatar((String)value);
      }
      break;

    case GENDER:
      if (value == null) {
        unsetGender();
      } else {
        setGender((Gender)value);
      }
      break;

    case SIGNATURE:
      if (value == null) {
        unsetSignature();
      } else {
        setSignature((String)value);
      }
      break;

    case TEL:
      if (value == null) {
        unsetTel();
      } else {
        setTel((String)value);
      }
      break;

    }
  }

  public Object getFieldValue(_Fields field) {
    switch (field) {
    case USER_ID:
      return Long.valueOf(getUserId());

    case NICK_NAME:
      return getNickName();

    case AVATAR:
      return getAvatar();

    case GENDER:
      return getGender();

    case SIGNATURE:
      return getSignature();

    case TEL:
      return getTel();

    }
    throw new IllegalStateException();
  }

  /** Returns true if field corresponding to fieldID is set (has been assigned a value) and false otherwise */
  public boolean isSet(_Fields field) {
    if (field == null) {
      throw new IllegalArgumentException();
    }

    switch (field) {
    case USER_ID:
      return isSetUserId();
    case NICK_NAME:
      return isSetNickName();
    case AVATAR:
      return isSetAvatar();
    case GENDER:
      return isSetGender();
    case SIGNATURE:
      return isSetSignature();
    case TEL:
      return isSetTel();
    }
    throw new IllegalStateException();
  }

  @Override
  public boolean equals(Object that) {
    if (that == null)
      return false;
    if (that instanceof UserInfo)
      return this.equals((UserInfo)that);
    return false;
  }

  public boolean equals(UserInfo that) {
    if (that == null)
      return false;

    boolean this_present_userId = true;
    boolean that_present_userId = true;
    if (this_present_userId || that_present_userId) {
      if (!(this_present_userId && that_present_userId))
        return false;
      if (this.userId != that.userId)
        return false;
    }

    boolean this_present_nickName = true && this.isSetNickName();
    boolean that_present_nickName = true && that.isSetNickName();
    if (this_present_nickName || that_present_nickName) {
      if (!(this_present_nickName && that_present_nickName))
        return false;
      if (!this.nickName.equals(that.nickName))
        return false;
    }

    boolean this_present_avatar = true && this.isSetAvatar();
    boolean that_present_avatar = true && that.isSetAvatar();
    if (this_present_avatar || that_present_avatar) {
      if (!(this_present_avatar && that_present_avatar))
        return false;
      if (!this.avatar.equals(that.avatar))
        return false;
    }

    boolean this_present_gender = true && this.isSetGender();
    boolean that_present_gender = true && that.isSetGender();
    if (this_present_gender || that_present_gender) {
      if (!(this_present_gender && that_present_gender))
        return false;
      if (!this.gender.equals(that.gender))
        return false;
    }

    boolean this_present_signature = true && this.isSetSignature();
    boolean that_present_signature = true && that.isSetSignature();
    if (this_present_signature || that_present_signature) {
      if (!(this_present_signature && that_present_signature))
        return false;
      if (!this.signature.equals(that.signature))
        return false;
    }

    boolean this_present_tel = true && this.isSetTel();
    boolean that_present_tel = true && that.isSetTel();
    if (this_present_tel || that_present_tel) {
      if (!(this_present_tel && that_present_tel))
        return false;
      if (!this.tel.equals(that.tel))
        return false;
    }

    return true;
  }

  @Override
  public int hashCode() {
    List<Object> list = new ArrayList<Object>();

    boolean present_userId = true;
    list.add(present_userId);
    if (present_userId)
      list.add(userId);

    boolean present_nickName = true && (isSetNickName());
    list.add(present_nickName);
    if (present_nickName)
      list.add(nickName);

    boolean present_avatar = true && (isSetAvatar());
    list.add(present_avatar);
    if (present_avatar)
      list.add(avatar);

    boolean present_gender = true && (isSetGender());
    list.add(present_gender);
    if (present_gender)
      list.add(gender.getValue());

    boolean present_signature = true && (isSetSignature());
    list.add(present_signature);
    if (present_signature)
      list.add(signature);

    boolean present_tel = true && (isSetTel());
    list.add(present_tel);
    if (present_tel)
      list.add(tel);

    return list.hashCode();
  }

  @Override
  public int compareTo(UserInfo other) {
    if (!getClass().equals(other.getClass())) {
      return getClass().getName().compareTo(other.getClass().getName());
    }

    int lastComparison = 0;

    lastComparison = Boolean.valueOf(isSetUserId()).compareTo(other.isSetUserId());
    if (lastComparison != 0) {
      return lastComparison;
    }
    if (isSetUserId()) {
      lastComparison = org.apache.thrift.TBaseHelper.compareTo(this.userId, other.userId);
      if (lastComparison != 0) {
        return lastComparison;
      }
    }
    lastComparison = Boolean.valueOf(isSetNickName()).compareTo(other.isSetNickName());
    if (lastComparison != 0) {
      return lastComparison;
    }
    if (isSetNickName()) {
      lastComparison = org.apache.thrift.TBaseHelper.compareTo(this.nickName, other.nickName);
      if (lastComparison != 0) {
        return lastComparison;
      }
    }
    lastComparison = Boolean.valueOf(isSetAvatar()).compareTo(other.isSetAvatar());
    if (lastComparison != 0) {
      return lastComparison;
    }
    if (isSetAvatar()) {
      lastComparison = org.apache.thrift.TBaseHelper.compareTo(this.avatar, other.avatar);
      if (lastComparison != 0) {
        return lastComparison;
      }
    }
    lastComparison = Boolean.valueOf(isSetGender()).compareTo(other.isSetGender());
    if (lastComparison != 0) {
      return lastComparison;
    }
    if (isSetGender()) {
      lastComparison = org.apache.thrift.TBaseHelper.compareTo(this.gender, other.gender);
      if (lastComparison != 0) {
        return lastComparison;
      }
    }
    lastComparison = Boolean.valueOf(isSetSignature()).compareTo(other.isSetSignature());
    if (lastComparison != 0) {
      return lastComparison;
    }
    if (isSetSignature()) {
      lastComparison = org.apache.thrift.TBaseHelper.compareTo(this.signature, other.signature);
      if (lastComparison != 0) {
        return lastComparison;
      }
    }
    lastComparison = Boolean.valueOf(isSetTel()).compareTo(other.isSetTel());
    if (lastComparison != 0) {
      return lastComparison;
    }
    if (isSetTel()) {
      lastComparison = org.apache.thrift.TBaseHelper.compareTo(this.tel, other.tel);
      if (lastComparison != 0) {
        return lastComparison;
      }
    }
    return 0;
  }

  public _Fields fieldForId(int fieldId) {
    return _Fields.findByThriftId(fieldId);
  }

  public void read(org.apache.thrift.protocol.TProtocol iprot) throws org.apache.thrift.TException {
    schemes.get(iprot.getScheme()).getScheme().read(iprot, this);
  }

  public void write(org.apache.thrift.protocol.TProtocol oprot) throws org.apache.thrift.TException {
    schemes.get(oprot.getScheme()).getScheme().write(oprot, this);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder("UserInfo(");
    boolean first = true;

    sb.append("userId:");
    sb.append(this.userId);
    first = false;
    if (!first) sb.append(", ");
    sb.append("nickName:");
    if (this.nickName == null) {
      sb.append("null");
    } else {
      sb.append(this.nickName);
    }
    first = false;
    if (isSetAvatar()) {
      if (!first) sb.append(", ");
      sb.append("avatar:");
      if (this.avatar == null) {
        sb.append("null");
      } else {
        sb.append(this.avatar);
      }
      first = false;
    }
    if (isSetGender()) {
      if (!first) sb.append(", ");
      sb.append("gender:");
      if (this.gender == null) {
        sb.append("null");
      } else {
        sb.append(this.gender);
      }
      first = false;
    }
    if (isSetSignature()) {
      if (!first) sb.append(", ");
      sb.append("signature:");
      if (this.signature == null) {
        sb.append("null");
      } else {
        sb.append(this.signature);
      }
      first = false;
    }
    if (isSetTel()) {
      if (!first) sb.append(", ");
      sb.append("tel:");
      if (this.tel == null) {
        sb.append("null");
      } else {
        sb.append(this.tel);
      }
      first = false;
    }
    sb.append(")");
    return sb.toString();
  }

  public void validate() throws org.apache.thrift.TException {
    // check for required fields
    // check for sub-struct validity
  }

  private void writeObject(java.io.ObjectOutputStream out) throws java.io.IOException {
    try {
      write(new org.apache.thrift.protocol.TCompactProtocol(new org.apache.thrift.transport.TIOStreamTransport(out)));
    } catch (org.apache.thrift.TException te) {
      throw new java.io.IOException(te);
    }
  }

  private void readObject(java.io.ObjectInputStream in) throws java.io.IOException, ClassNotFoundException {
    try {
      // it doesn't seem like you should have to do this, but java serialization is wacky, and doesn't call the default constructor.
      __isset_bitfield = 0;
      read(new org.apache.thrift.protocol.TCompactProtocol(new org.apache.thrift.transport.TIOStreamTransport(in)));
    } catch (org.apache.thrift.TException te) {
      throw new java.io.IOException(te);
    }
  }

  private static class UserInfoStandardSchemeFactory implements SchemeFactory {
    public UserInfoStandardScheme getScheme() {
      return new UserInfoStandardScheme();
    }
  }

  private static class UserInfoStandardScheme extends StandardScheme<UserInfo> {

    public void read(org.apache.thrift.protocol.TProtocol iprot, UserInfo struct) throws org.apache.thrift.TException {
      org.apache.thrift.protocol.TField schemeField;
      iprot.readStructBegin();
      while (true)
      {
        schemeField = iprot.readFieldBegin();
        if (schemeField.type == org.apache.thrift.protocol.TType.STOP) { 
          break;
        }
        switch (schemeField.id) {
          case 1: // USER_ID
            if (schemeField.type == org.apache.thrift.protocol.TType.I64) {
              struct.userId = iprot.readI64();
              struct.setUserIdIsSet(true);
            } else { 
              org.apache.thrift.protocol.TProtocolUtil.skip(iprot, schemeField.type);
            }
            break;
          case 2: // NICK_NAME
            if (schemeField.type == org.apache.thrift.protocol.TType.STRING) {
              struct.nickName = iprot.readString();
              struct.setNickNameIsSet(true);
            } else { 
              org.apache.thrift.protocol.TProtocolUtil.skip(iprot, schemeField.type);
            }
            break;
          case 3: // AVATAR
            if (schemeField.type == org.apache.thrift.protocol.TType.STRING) {
              struct.avatar = iprot.readString();
              struct.setAvatarIsSet(true);
            } else { 
              org.apache.thrift.protocol.TProtocolUtil.skip(iprot, schemeField.type);
            }
            break;
          case 4: // GENDER
            if (schemeField.type == org.apache.thrift.protocol.TType.I32) {
              struct.gender = com.lvxingpai.yunkai.Gender.findByValue(iprot.readI32());
              struct.setGenderIsSet(true);
            } else { 
              org.apache.thrift.protocol.TProtocolUtil.skip(iprot, schemeField.type);
            }
            break;
          case 5: // SIGNATURE
            if (schemeField.type == org.apache.thrift.protocol.TType.STRING) {
              struct.signature = iprot.readString();
              struct.setSignatureIsSet(true);
            } else { 
              org.apache.thrift.protocol.TProtocolUtil.skip(iprot, schemeField.type);
            }
            break;
          case 6: // TEL
            if (schemeField.type == org.apache.thrift.protocol.TType.STRING) {
              struct.tel = iprot.readString();
              struct.setTelIsSet(true);
            } else { 
              org.apache.thrift.protocol.TProtocolUtil.skip(iprot, schemeField.type);
            }
            break;
          default:
            org.apache.thrift.protocol.TProtocolUtil.skip(iprot, schemeField.type);
        }
        iprot.readFieldEnd();
      }
      iprot.readStructEnd();
      struct.validate();
    }

    public void write(org.apache.thrift.protocol.TProtocol oprot, UserInfo struct) throws org.apache.thrift.TException {
      struct.validate();

      oprot.writeStructBegin(STRUCT_DESC);
      oprot.writeFieldBegin(USER_ID_FIELD_DESC);
      oprot.writeI64(struct.userId);
      oprot.writeFieldEnd();
      if (struct.nickName != null) {
        oprot.writeFieldBegin(NICK_NAME_FIELD_DESC);
        oprot.writeString(struct.nickName);
        oprot.writeFieldEnd();
      }
      if (struct.avatar != null) {
        if (struct.isSetAvatar()) {
          oprot.writeFieldBegin(AVATAR_FIELD_DESC);
          oprot.writeString(struct.avatar);
          oprot.writeFieldEnd();
        }
      }
      if (struct.gender != null) {
        if (struct.isSetGender()) {
          oprot.writeFieldBegin(GENDER_FIELD_DESC);
          oprot.writeI32(struct.gender.getValue());
          oprot.writeFieldEnd();
        }
      }
      if (struct.signature != null) {
        if (struct.isSetSignature()) {
          oprot.writeFieldBegin(SIGNATURE_FIELD_DESC);
          oprot.writeString(struct.signature);
          oprot.writeFieldEnd();
        }
      }
      if (struct.tel != null) {
        if (struct.isSetTel()) {
          oprot.writeFieldBegin(TEL_FIELD_DESC);
          oprot.writeString(struct.tel);
          oprot.writeFieldEnd();
        }
      }
      oprot.writeFieldStop();
      oprot.writeStructEnd();
    }

  }

  private static class UserInfoTupleSchemeFactory implements SchemeFactory {
    public UserInfoTupleScheme getScheme() {
      return new UserInfoTupleScheme();
    }
  }

  private static class UserInfoTupleScheme extends TupleScheme<UserInfo> {

    @Override
    public void write(org.apache.thrift.protocol.TProtocol prot, UserInfo struct) throws org.apache.thrift.TException {
      TTupleProtocol oprot = (TTupleProtocol) prot;
      BitSet optionals = new BitSet();
      if (struct.isSetUserId()) {
        optionals.set(0);
      }
      if (struct.isSetNickName()) {
        optionals.set(1);
      }
      if (struct.isSetAvatar()) {
        optionals.set(2);
      }
      if (struct.isSetGender()) {
        optionals.set(3);
      }
      if (struct.isSetSignature()) {
        optionals.set(4);
      }
      if (struct.isSetTel()) {
        optionals.set(5);
      }
      oprot.writeBitSet(optionals, 6);
      if (struct.isSetUserId()) {
        oprot.writeI64(struct.userId);
      }
      if (struct.isSetNickName()) {
        oprot.writeString(struct.nickName);
      }
      if (struct.isSetAvatar()) {
        oprot.writeString(struct.avatar);
      }
      if (struct.isSetGender()) {
        oprot.writeI32(struct.gender.getValue());
      }
      if (struct.isSetSignature()) {
        oprot.writeString(struct.signature);
      }
      if (struct.isSetTel()) {
        oprot.writeString(struct.tel);
      }
    }

    @Override
    public void read(org.apache.thrift.protocol.TProtocol prot, UserInfo struct) throws org.apache.thrift.TException {
      TTupleProtocol iprot = (TTupleProtocol) prot;
      BitSet incoming = iprot.readBitSet(6);
      if (incoming.get(0)) {
        struct.userId = iprot.readI64();
        struct.setUserIdIsSet(true);
      }
      if (incoming.get(1)) {
        struct.nickName = iprot.readString();
        struct.setNickNameIsSet(true);
      }
      if (incoming.get(2)) {
        struct.avatar = iprot.readString();
        struct.setAvatarIsSet(true);
      }
      if (incoming.get(3)) {
        struct.gender = com.lvxingpai.yunkai.Gender.findByValue(iprot.readI32());
        struct.setGenderIsSet(true);
      }
      if (incoming.get(4)) {
        struct.signature = iprot.readString();
        struct.setSignatureIsSet(true);
      }
      if (incoming.get(5)) {
        struct.tel = iprot.readString();
        struct.setTelIsSet(true);
      }
    }
  }

}

