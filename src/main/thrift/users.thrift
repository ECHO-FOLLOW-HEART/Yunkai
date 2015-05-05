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

exception NotFoundException {
    1: string message;
}

service userservice {
  UserInfo getUserById(1:i64 userId) throws (1:NotFoundException ex)
}