import org.scalatest.{ BeforeAndAfterAll, FlatSpecLike, Matchers }
import akka.actor.{ Actor, Props, ActorSystem }
import akka.testkit.{ ImplicitSender, TestKit, TestActorRef }
import scala.concurrent.duration._


import org.scalatest.FunSuite

class AuctionSpec extends FunSuite{
  val comic = Comic("Marvel Super-Heroes vol. 2, #8", "Squirrel Girl",1992, "Steve Ditko")
  test("get unique name") {
    
    assert(comic.getUniqueName() == "Squirrel_Girl__1992__Steve_Ditko")
    
  }
  
  trait Person
  case class Ben(name:String) extends Person
  case class John(name:String) extends Person
  case class Neo(name:String) extends Person
  case object Steve extends Person
 
  
  def getTheDudesName(t:Person):Option[String] = t match {
    case Ben(name) => Some(name)
    case John(name) => Some(name + "1")
    case _ => None
  }
  
  
  trait Monad[A]{
    def map[B](f: A => B):Monad[B] = ???
    def flatMap[B](f: A => Monad[B]):Monad[B] = ???
  }
  
  
  val x:Option[String] = getTheDudesName(Ben("ben smith"))
  val hat = x.map(str => str.map(char => char.toInt).reduce((a, b) => a + b))
  
  List(1,2,3).flatMap(sh => "a")
  
  test("pass a non-defined class") {
      val someBidder = new SomeBidder(comic, "Ryan")
      assert(someBidder.receive(Steve) == "sda")
  }

}