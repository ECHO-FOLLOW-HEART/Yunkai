namespace java com.lvxingpai.smscenter.java
#@namespace scala com.lvxingpai.smscenter

exception SmsCenterException {
  1:i32 code,
  2:string message
}

service SmsCenter {
  string sendSms(1:string message, 2:list<string> recipients) throws (1:SmsCenterException ex)
}