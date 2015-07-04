namespace java com.lvxingpai.yunkai.java
#@namespace scala com.lvxingpai.yunkai

// 用户性别
enum Gender {
  MALE,
  FEMALE,
  SECRET
}

// 聊天群组的类型。CHATGROUP为普通讨论组
enum GroupType{
  CHATGROUP,
  FORUM
}

// 由一个用户向另外一个用户发起的好友申请
struct ContactRequest {
  1:string id,
  2:i64 sender,
  3:i64 receiver,
  4:i32 status,
  5:string requestMessage,
  6:string rejectMessage
  7:i64 timestamp,
  8:i64 expire
}

struct UserInfo {
  1: string id,
  2: i64 userId,
  3: string nickName,
  4: optional string avatar,
  5: optional Gender gender,
  6: optional string signature,
  7: optional string tel,
}

//Created by pengyt on 2015/5/26.
struct ChatGroup {
  1: string id,
  2: i64 chatGroupId,
  3: string name,
  4: optional string groupDesc,
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

// 表示验证码所对应的动作
enum ValidationCodeAction {
  SIGNUP = 1            // 注册
  RESET_PASSWORD = 2    // 重置密码
  UPDATE_TEL = 3        // 绑定手机
}

struct Token {
  1:string fingerprint
  2:ValidationCodeAction action
  3:optional i64 userId
  4:optional i32 countryCode
  5:optional string tel
  6:i64 createTime
}

enum UserInfoProp {
  ID,
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
  ID,
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
  1:optional string message;
}

exception InvalidArgsException {
  1:optional string message;
}

exception AuthException {
  1:optional string message
}

exception UserExistsException {
  1:optional string message
}

exception GroupMembersLimitException {
  1:optional string message
}

exception InvalidStateException {
  1:optional string message
}

// 验证码验证失败的异常
exception ValidationCodeException {
  1:optional string message
}

// API流量限制的异常
exception OverQuotaLimitException {
  1:optional string message
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
  string sendContactRequest(1:i64 sender, 2:i64 receiver, 3:optional string message) throws (1:NotFoundException ex1, 2:InvalidArgsException ex2, 3:InvalidStateException ex3)

  // 接受好友请求
  void acceptContactRequest(1:string requestId) throws (1:NotFoundException ex, 2:InvalidStateException ex2)

  // 拒绝好友请求
  void rejectContactRequest(1:string requestId, 2:optional string message) throws (1:NotFoundException ex1, 2:InvalidArgsException ex2, 3:InvalidStateException ex3)

  // 取消好友请求
  void cancelContactRequest(1:string requestId) throws (1:NotFoundException ex)

  list<ContactRequest> getContactRequests(1:i64 userId, 2:optional i32 offset, 3:optional i32 limit) throws (1:NotFoundException ex)

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

  // 第3个参数表示登录设备的来源, 比如：web或者安卓
  UserInfo login(1:string loginName, 2:string password, 3:string source) throws (1:AuthException ex)

  // 验证用户密码
  bool verifyCredential(1:i64 userId, 2:string password) throws (1:AuthException ex)

  // 发送手机验证码
  // 如果发送过于频繁，会出现OverQuotaLimitException
  // 如果参数不合法，比如既不提供tel，又不提供userId，会抛出InvalidArgsException
  void sendValidationCode(1:ValidationCodeAction action, 2:optional i32 countryCode, 3:string tel, 4:optional i64 userId) throws (1:OverQuotaLimitException ex, 2:InvalidArgsException ex2)

//   根据fingerprint读取Token
//  Token fetchToken(1:string fingerprint) throws (1:NotFoundException ex)

  string checkValidationCode(1:string code, 2:ValidationCodeAction action, 3:optional i32 countryCode, 4:optional string tel, 5:optional i64 userId) throws (1:ValidationCodeException ex)

  // 用户修改密码（如果原先没有密码，则oldPassword可以设置为""）
  // AuthException: 旧密码错误
  // InvalidArgsException: 新密码不合法（必须是ASCII 33~126之间的字符，且长度为6~32）
  void resetPassword(1:i64 userId, 2:string oldPassword, 3:string newPassword) throws (1:InvalidArgsException ex1, 2:AuthException ex2)

  // 通过提供token的方式修改密码
  void resetPasswordByToken(1:i64 userId, 2:string newPassword, 3:string token) throws (1:InvalidArgsException ex1, 2:AuthException ex2)

  // 修改手机号
  void updateTelNumber(1:i64 userId, 2:string tel, 3:string token) throws (1:NotFoundException ex1, 2:InvalidArgsException ex2)

  // 新用户注册。支持的UserInfoProp暂时只有tel
  UserInfo createUser(1:string nickName, 2:string password, 3:optional map<UserInfoProp, string> miscInfo) throws (1: UserExistsException ex1, 2: InvalidArgsException ex2)

  // 搜索用户(参数1表示根据哪些字段搜索, 参数2表示返回的字段, 参数3表示当前页从第几个开始, 4表示一页返回多少个)
  list<UserInfo> searchUserInfo(1: map<UserInfoProp, string> queryFields, 2: optional list<UserInfoProp> fields, 3: optional i32 offset, 4: optional i32 count)

  // 用户退出登录
  // void logout(1: i64 userId)

  // 创建讨论组。支持的ChatGroupProp有：name, groupDesc, avatar, maxUsers以及visible
  ChatGroup createChatGroup(1: i64 creator, 2: list<i64> participants, 3: optional map<ChatGroupProp, string> chatGroupProps)
    throws (1: InvalidArgsException ex1, 2: NotFoundException ex2, 3: GroupMembersLimitException ex3)

  // 搜索讨论组
//  list<ChatGroup> searchChatGroup(1: string keyword)

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
  list<i64> addChatGroupMembers(1: i64 chatGroupId, 2: i64 operatorId, 3: list<i64> userIds) throws (1:NotFoundException ex)

  // 批量删除讨论组成员
  list<i64> removeChatGroupMembers(1: i64 chatGroupId, 2: i64 operatorId, 3: list<i64> userIds) throws (1:NotFoundException ex)

  // 获得讨论组成员
  list<UserInfo> getChatGroupMembers(1:i64 chatGroupId, 2:optional list<UserInfoProp> fields) throws (1:NotFoundException ex)
}
