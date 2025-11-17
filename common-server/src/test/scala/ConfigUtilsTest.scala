import org.chats.settings.initConfig
import org.scalatest.flatspec.AnyFlatSpec
import scala.jdk.CollectionConverters.given

class ConfigUtilsTest extends AnyFlatSpec {
  "initConfig" should "override with an environment variable" in {
    setEnv("CASSANDRA_SESSION_LOCALDATACENTER", "test-dc")

    val config = initConfig("application-test.conf")
    val listValue = config.getString("cassandra.session.local-datacenter")
    assert(listValue == "test-dc")
  }

  "initConfig" should "parse list properties from env variables" in {
    setEnv("CASSANDRA_SESSION_CONTACTPOINT", "127.0.0.2")

    val config = initConfig("application-test.conf")
    val listValue = config.getList("cassandra.session.contact-point")
    assert(listValue.unwrapped().asScala.toSeq == Seq("127.0.0.2"))
  }

  def setEnv(key: String, value: String) = {
    val field = System.getenv().getClass.getDeclaredField("m")
    field.setAccessible(true)
    val map = field.get(System.getenv()).asInstanceOf[java.util.Map[java.lang.String, java.lang.String]]
    map.put(key, value)
  }
}
