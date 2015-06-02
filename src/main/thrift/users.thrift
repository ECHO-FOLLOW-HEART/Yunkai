namespace java com.aizou.yunkai
#@namespace scala com.aizou.yunkai

enum Gender {
  MALE,
  FEMALE
}

enum GroupType{
  CHAT_GROUP
  GROUP
}

struct UserInfo {
  1: i64 userId,
  2: string nickName,
  3: optional string avatar,
  4: optional Gender gender,
  5: optional string signature,
  6: optional string tel,
}

//Created by pengyt on 2015/5/26.
struct ChatGroup{
  1: i64 chatGroupId,
  2: string name,
  3: optional string groupDesc,
  4: GroupType groupType,
  5: optional string avatar,
  6: optional list<string> tags,
  7: i64 creator,
  8: list<i64> admin,
  9: list<i64> participants,
  //10: i32 participantCnt,
  //10: optional i64 msgCounter,
  10: i32 maxUsers,
  11: i64 createTime,
  12: i64 updateTime,
  13: bool visible
}

enum UserInfoProp {
  USER_ID,
  NICK_NAME,
  AVATAR,
  GENDER,
  SIGNATURE,
  TEL
}

//Created by pengyt on 2015/5/26.
enum ChatGroupProp{
  CHAT_GROUP_ID,
  NAME,
  GROUP_DESC,
  GROUP_TYPE,
  AVATAR,
  TAGS,
  CREATOR,
  ADMIN,
  PARTICIPANTS,
  //PARTICIPANTCNT,
  //MSG_COUNTER,
  MAX_USERS,
  CREATE_TIME,
  UPDATE_TIME,
  VISIBLE
}

exception NotFoundException {
    1: string message;
}

exception InvalidArgsException {
    1: string message;
}

exception AuthException {
    1: string message
}

service userservice {
  UserInfo getUserById(1:i64 userId) throws (1:NotFoundException ex)

  void updateUserInfo(1:i64 userId, 2:map<UserInfoProp, string> userInfo)

  bool isContact(1:i64 userA, 2:i64 userB)

  void addContact(1:i64 userA, 2:i64 userB)

  void addContacts(1:i64 userA, 2:list<i64> targets)

  void removeContact(1:i64 userA, 2:i64 userB)

  void removeContacts(1:i64 userA, 2:list<i64> targets)

  list<UserInfo> getContactList(1:i64 userId, 2: optional list<UserInfoProp> fields,
    3: optional i32 offset, 4: optional i32 count)

  UserInfo login(1:string loginName, 2:string password) throws (1:AuthException ex)

  // Created by pengyt on 2015/5/26.
  // 群名称备注（和群成员id关联，某个群成员将群备注修改了）和群成员备注后面再做
  // 新用户注册
  UserInfo createUser(1:string nickName, 2:string password, 3:optional string tel) throws (1: InvalidArgsException ex)

  // 用户退出登录
  // void logout(1: i64 userId)

  // 创建讨论组
  ChatGroup createChatGroup(1:i64 creator, 2:string name, 3:list<i64> participants, 4:map<ChatGroupProp, string> chatGroupProps) throws (1: InvalidArgsException ex)

  // 搜索讨论组
  // list<ChatGroup> searchChatGroup(1: string keyword)

  // 修改讨论组信息（比如名称、描述等）
  ChatGroup updateChatGroup(1: i64 chatGroupId, 2:map<ChatGroupProp, string> chatGroupProps) throws (1:InvalidArgsException ex1, 2:NotFoundException ex2)

  // 获取讨论组信息
  ChatGroup getChatGroup(1: i64 chatGroupId) throws (1:NotFoundException ex)

  // 获取用户讨论组信息
  list<ChatGroup> getUserChatGroups(1: i64 userId 2: optional list<ChatGroupProp> fields) throws (1:NotFoundException ex)

  // 批量添加讨论组成员
  void addChatGroupMembers(1: i64 chatGroupId, 2: list<i64> userIds) throws (1:NotFoundException ex)

  // 批量删除讨论组成员
  void removeChatGroupMembers(1: i64 chatGroupId, 2: list<i64> userIds) throws (1:NotFoundException ex)

  // 获得讨论组成员
  list<UserInfo> getChatGroupMembers(1: i64 chatGroupId, 2: optional list<UserInfoProp> fields) throws (1:NotFoundException ex)
}