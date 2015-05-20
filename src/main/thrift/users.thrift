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

  // userA和userB是否为好友
  bool isContact(1:i64 userA, 2:i64 userB)

  // 将userA和userB相互加为好友
  void addContact(1:i64 userA, 2:i64 userB)

  void addContacts(1:i64 userA, 2:list<i64> targets)

  // 移除userA和userB的好友关系
  void removeContact(1:i64 userA, 2:i64 userB)

  void removeContacts(1:i64 userA, 2:list<i64> targets)

  list<UserInfo> getContactList(1:i64 userId, 2: optional list<UserInfoProp> fields,
    3: optional i32 offset, 4: optional i32 count)
}