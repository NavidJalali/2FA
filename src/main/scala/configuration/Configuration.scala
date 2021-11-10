package configuration

import com.typesafe.config.{Config, ConfigFactory}
import model.HexString
import zio.{Has, ULayer, ZLayer}

import scala.jdk.CollectionConverters.MapHasAsJava

object Configuration {
  trait FinalConfig {
    val permissionSecret: HexString
    val authSecret: HexString
    val databaseConfig: Config
    val godScopeSecret: String
    val port: Int
  }

  val local: ULayer[Has[FinalConfig]] =
    ZLayer.succeed(
      new FinalConfig {
        override val permissionSecret: HexString =
          HexString.fromBase64("kiaH5KbZtKrxu8e4wctTQ1xb6bi3tEimwOSuQNdgQq4XgRdO48skhJcitrphAkJjlu7wv314V40AikhfvQfh+oBl4xV68/Stddcm9Mp9bzUVayTlb/vQIVRfseK6TtgrshubjT54YnonUObxpR/yWSsUv4tF4xoCaLM5efTqRjgxleaFj8CqTzjCtkxhqisefgRx2u6H5oj21YBC9HVHn4BO7+S5bNLbW5lN9XDjXpyU/RnghvZ5L5ReTxg9kLWzT6eTZUkYmFgLoJcWAfStONcnHkF0sNfZp2T4Iy3IxMsbvwXLovSSyiE5aKEdxTWAWZe67+2Rr1ZjRUPGqMPNSp4tdoEmaShqGTa88I5WY1vza0Fnj6/832JU0dxm+nmDW7ya2RLytztReA9JQ87Jeh9I2urXtLTdsYo2/xqAqD7o0urZkXAwkHEAWwmNG7oSLNZBwnGJsUM8FWon76PGAp8KNRYxNyhQocpwB+KC7ojWk9Iqrwb26kxNexATOs0gH0b1s52CMOwF2GRl1As0WsMx+n9GPJbJHT2kMaxtHENQgAaWWwmeW9KS9EWKqbvdz7IeauH4URfnBMUdEUyP/Yt27X9PTNwerz/mmfvkRhb+bEvud8gWgt/4hMUvOOaDZ45P8Hftec8jGqKwy0BKwNpkT2E3ORhGLNwWppEYc/Q=")
            .get

        override val authSecret: HexString =
          HexString.fromBase64("Rzuk6hVbJdziSRb8/+AFjPI526mhgKtIT3RnhA+amfbxKuX+swDcdN7xyPYRZPeXhYQoXTFqTA3g9rmeQJQqu9rbYdpN3zKpiFVIM0DzRbvFK0J06e1ntmiK0VLrAl7ZydVfHkD1GPiorjRGGKM6PNrMNXC929js8qbMF5+GyzwU/5xaSOcfpyb3ZpfuRYDsT5uIxEaEEWjyFx/5zEu2sxaTpyYoMUvkFK65gwVSAQvoYfeihwPJBQ6sXK9CDANUovQB6F1MHG+XcHYR0E8KpVeycLYwtbhKIAunTTUixgjEOlVlJPp4Bq041lCJLvYt4LeUHxxKazak7S3G/40EtSTC6tQ/RjPl8Siq4YBHsluLs6fqwyStvyC1wE6oZrXD0SU95008dOSawO+oUfvneOUiTcSCG/8htIcqup9GxoLGu6ZS0rKvSONQnOPHg4ceTLV30MIuq6dDTlWnWh6mSjKu+c56rZD+ooqq/w+rMpe9wRosY9z91mwYkBy0RkcIno2wsAdcdOu5lUJr5faGjfxlgmrrWyHIfjcpHQCU5ErMN7+8ehgu27jOdnPvxthXGALd1dSVaMuyLwJ8AqRbRcu/lrDLlbBg8eclJm6gB1PZNHh6dixEqviBDA7Xx/t6uFjQazV27Si+cXDFblhPxNEjOcAXntVmLDpAM4PoLo0=")
            .get

        override val databaseConfig: Config =
          ConfigFactory.parseMap(
            Map(
              "url" -> "jdbc:h2:mem:test1;DB_CLOSE_DELAY=-1",
              "driver" -> "org.h2.Driver",
              "connectionPool" -> "disabled"
            ).asJava
          )
        override val port: Int = 8080

        override val godScopeSecret: String = "god"
      }
    )
}
