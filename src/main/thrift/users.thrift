namespace java com.lvxingpai.yunkai
#@namespace scala com.lvxingpai.yunkai

enum Gender {
  MALE,
  FEMALE,
  SECRET
}

enum GroupType{
  CHATGROUP,
  FORUM
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
//  4: GroupType groupType,
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
  TEL,
  CHAT_GROUPS
}

//Created by pengyt on 2015/5/26.
enum ChatGroupProp{
  CHAT_GROUP_ID,
  NAME,
  GROUP_DESC,
  AVATAR,
  TAGS,
  CREATOR,
  ADMIN,
  PARTICIPANTS,
  MAX_USERS,
  VISIBLE
}

exception NotFoundException {
  1:string message;
}

exception InvalidArgsException {
  1:string message;
}

exception AuthException {
  1:string message
}

exception UserExistsException {
  1:string message
}

exception GroupMembersLimitException {
  1:string message
}

exception InvalidStateException {
  1:string message
}

service userservice {
  // 获得单个用户信息
  UserInfo getUserById(1:i64 userId, 2: optional list<UserInfoProp> fields) throws (1:NotFoundException ex)

  // 获得多个用户的信息
  map<i64, UserInfo> getUsersById(1:list<i64> userIdList, 2: optional list<UserInfoProp> fields)

  // 更新用户的信息。支持的UserInfoProp有：nickName, signature, gender和avatar
  UserInfo updateUserInfo(1:i64 userId, 2:map<UserInfoProp, string> userInfo) throws (1:NotFoundException ex1, 2:InvalidArgsException ex2)

  // 判断两个用户是否为好友关系
  bool isContact(1:i64 userA, 2:i64 userB) throws (1:NotFoundException ex)

  // 发送好友请求
  // sender/receiver: 由谁向谁发起请求
  // message: 请求附言
  string sendContactRequest(1:i64 sender, 2:i64 receiver, 3:optional string message) throws (1:NotFoundException ex1, 2:InvalidArgsException ex2)

  // 接受好友请求
  void acceptContactRequest(1:string requestId) throws (1:NotFoundException ex)

  // 拒绝好友请求
  void rejectContactRequest(1:string requestId, 2:optional string message) throws (1:NotFoundException ex1, 2:InvalidArgsException ex2)

  // 取消好友请求
  void cancelContactRequest(1:string requestId) throws (1:NotFoundException ex)

  // 添加单个好友
  void addContact(1:i64 userA, 2:i64 userB) throws (1:NotFoundException ex)

  // 批量添加好友
  void addContacts(1:i64 userA, 2:list<i64> targets) throws (1:NotFoundException ex)

  // 删除单个好友
  void removeContact(1:i64 userA, 2:i64 userB) throws (1:NotFoundException ex)

  // 批量删除好友
  void removeContacts(1:i64 userA, 2:list<i64> targets) throws (1:NotFoundException ex)

  // 获得用户的好友列表
  list<UserInfo> getContactList(1:i64 userId, 2: optional list<UserInfoProp> fields, 3:optional i32 offset,
    4:optional i32 count) throws (1:NotFoundException ex)

  // 获得用户的好友个数
  i32 getContactCount(1:i64 userId) throws (1:NotFoundException ex)

  UserInfo login(1:string loginName, 2:string password) throws (1:AuthException ex)

  // 用户修改密码
  void resetPassword(1:i64 userId, 2:string oldPassword, 3:string newPassword) throws (1: InvalidArgsException ex1, 2: AuthException ex2)

  // 新用户注册。支持的UserInfoProp暂时只有tel
  UserInfo createUser(1:string nickName, 2:string password, 3:optional map<UserInfoProp, string> miscInfo) throws (1: UserExistsException ex1, 2: InvalidArgsException ex2)

  // 用户退出登录
  // void logout(1: i64 userId)

  // 创建讨论组。支持的ChatGroupProp有：name, groupDesc, avatar, maxUsers以及visible
  ChatGroup createChatGroup(1: i64 creator, 2: list<i64> participants, 3: optional map<ChatGroupProp, string> chatGroupProps)
    throws (1: InvalidArgsException ex1, 2: NotFoundException ex2)

  // 搜索讨论组
  // list<ChatGroup> searchChatGroup(1: string keyword)

  // 修改讨论组信息（比如名称、描述等）。支持的ChatGroupProp有：name, groupDesc, maxUsers, avatar和visible
  ChatGroup updateChatGroup(1: i64 chatGroupId, 2: map<ChatGroupProp, string> chatGroupProps) throws (1: InvalidArgsException ex1, 2: NotFoundException ex2)

  // 获取讨论组信息
  ChatGroup getChatGroup(1: i64 chatGroupId, 2: optional list<ChatGroupProp> fields) throws (1:NotFoundException ex)

  // 批量获取讨论组信息
  map<i64, ChatGroup> getChatGroups(1:list<i64> groupIdList, 2:optional list<ChatGroupProp> fields)

  // 获取用户所参加的讨论组列表
  list<ChatGroup> getUserChatGroups(1: i64 userId 2: optional list<ChatGroupProp> fields, 3: optional i32 offset,
    4: optional i32 count) throws (1:NotFoundException ex)

  // 获得用户所参加的讨论组个数
  i32 getUserChatGroupCount(1: i64 userId) throws (1: NotFoundException ex)

  // 批量添加讨论组成员
  list<i64> addChatGroupMembers(1: i64 chatGroupId, 2: list<i64> userIds) throws (1:NotFoundException ex)

  // 批量删除讨论组成员
  list<i64> removeChatGroupMembers(1: i64 chatGroupId, 2: list<i64> userIds) throws (1:NotFoundException ex)

  // 获得讨论组成员
  list<UserInfo> getChatGroupMembers(1:i64 chatGroupId, 2:optional list<UserInfoProp> fields) throws (1:NotFoundException ex)
}