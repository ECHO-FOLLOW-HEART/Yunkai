namespace java com.aizou.yunkai
#@namespace scala com.aizou.yunkai

enum Gender {
  MALE,
  FEMALE
}

struct UserInfo {
  1: i64 userId,
  2: string nickName,
  3: optional string avatar,
  4: optional Gender gender,
  5: optional string signature,
  6: optional string tel,
}

enum UserInfoProp {
  USER_ID,
  NICK_NAME,
  AVATAR,
  GENDER,
  SIGNATURE,
  TEL
}

exception NotFoundException {
    1: string message;
}

exception InvalidArgsException {
    1: string message;
}

service userservice {
  UserInfo getUserById(1:i64 userId) throws (1:NotFoundException ex)

  void updateUserInfo(1:i64 userId, 2:map<UserInfoProp, string> userInfo)
}