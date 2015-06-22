import org.specs2.specification.core.SpecStructure

/**
 * Created by zephyre on 6/19/15.
 */
class GroupManagerTest extends YunkaiBaseTest {
  override def is: SpecStructure =
    s2"""
        |This is a specification to check GroupManager
        |
        |The GroupManager should:
      """.stripMargin

  val chatGroup = {
    val userMaps = userPreset.toSeq map (_._2)
    val userIdList = userMaps map (_("userId").toLong)

    val creatorId = userIdList head
    val partiIdList = userIdList tail

    invoke(client.createChatGroup(creatorId, "Test", partiIdList, Map()))
  }

  override def beforeAll(): Unit = {
    super.beforeAll()
  }

  def getChatGroupInfo = {

  }
}
