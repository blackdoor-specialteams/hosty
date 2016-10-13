package black.door.hosty

import scala.collection.immutable.Map

/**
  * Created by nfischer on 10/11/2016.
  */
case class Service(
                    name: String,
                    image: String,
                    env: Map[String, String],
                    scale: Int,
                    ports: Map[Int, Int],
                    args: Seq[String]
                  )
