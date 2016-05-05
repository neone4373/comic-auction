import pprint.Config.Colors._
import akka.actor.{ ActorRef, ActorSystem, Props, Actor, Inbox }
import akka.cluster.{Cluster, MemberStatus}
import akka.stream.scaladsl.{FileIO, Flow, Source, Sink, Tcp,
	GraphDSL, RunnableGraph, Broadcast,
	Framing, BidiFlow}

import org.joda.time.DateTime
import scala.concurrent.duration._
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.pubsub.DistributedPubSubMediator.
  {Publish, Subscribe, Unsubscribe}


case object InquireAll
case object BestOffer
case object BeatenOffer
case object NotBestOffer
case object SendBid
case object Stop
case object AuctionInProgress

case class Winner(item: Comic, price: Int)
case class Inquire(item: Comic)
case class Bid(amount: Int, item: Comic)
case class StatusUpdate(item: Comic, currentBid: Int, endTime: Deadline)
case class Shrug(message: String)
case class Message(from: String, message: String, topic: String)
case class NewItem(from: String, message: String, topic: String)
case class AuctionOver(name: String, item: ActorRef)
case class AuctionItem(item: Comic, startingPrice: Int,
                       duration: FiniteDuration)
case class AuctionListItem(name: String, ref: ActorRef)

                       
case class Comic(name: String, superhero: String,
                 publishing_year: Int, author: String){
  def getUniqueName(): String = {
    val fmtd_superhero = superhero.replace(" ", "_")
    val fmtd_author = author.replace(" ", "_")
    s"${fmtd_superhero}__${publishing_year}__${fmtd_author}"
  }
  def getTopics() = {
    List(superhero, publishing_year.toString, author)
  }
}


class Auction(item: Comic, startingPrice: Int,
              duration: FiniteDuration) extends Actor {
  // TODO make persistant with PersistentActor
  val endTime = duration fromNow
  var currentBid = startingPrice
  var currentHighBidder: ActorRef = null
  val mediator = DistributedPubSub(context.system).mediator
  val topics = item.getTopics()
  val auctioneer = context.parent
  
  override def preStart() {
    val endDate = new DateTime(endTime.time.toMillis)
    val startupMessage = 
      s"""New auction started for: 
          |\titem: ${item.getUniqueName} starting 
          |\tprice: $startingPrice ends: ${endDate}""".stripMargin
    topics.foreach(topic => 
      mediator ! Publish(topic, 
                         NewItem(item.getUniqueName, startupMessage, topic)))
  }
  def auctionOver(sender: ActorRef) {
    if (sender != auctioneer) sender ! AuctionOver
    auctioneer ! AuctionOver(item.getUniqueName(), self)
    val prefix = s"Auction for ${item.getUniqueName} ended with: "
    val suffix = if (currentHighBidder == null) s"No Winner!"
                 else s"a high bid of $$$currentBid from $currentHighBidder!"
    val winning = s"""$prefix
                      |\t$suffix""".stripMargin
    currentHighBidder ! Winner(item, currentBid)
    item.getTopics.foreach{ topic => 
      mediator ! Publish(topic, Message(item.getUniqueName, winning, topic))}

  }
  // Disables the call the preStart
  override def postRestart(reason: Throwable): Unit = ()     
  def receive = {
    case Inquire => {
      if (endTime.isOverdue == true) {
        auctionOver(sender)
      } else {        
        sender ! StatusUpdate(item, currentBid, endTime)
      }
    }
    case Bid(amount, item) =>
      if (endTime.isOverdue == true) {
        auctionOver(sender)
      }
      else if (amount > currentBid) {
        if (currentHighBidder != null) currentHighBidder ! BeatenOffer 
        currentHighBidder = sender; currentBid = amount;
        sender ! BestOffer
        val high_bidder_message = 
          s"Item: ${item.getUniqueName} has a new high bid of $currentBid"
        item.getTopics.foreach{ topic =>
          mediator ! Publish(topic,
                             Message(item.getUniqueName,
                                     high_bidder_message,
                                     topic))}
      } else {
        sender ! NotBestOffer
      }
  }
}


class Auctioneer(auctionItems: List[AuctionItem]) extends Actor {
  // TODO make persistant with PersistentActor
  var liveAuctions: List[AuctionListItem] = List()
  var endedAuctions: List[AuctionListItem] = List()
  val mediator = DistributedPubSub(context.system).mediator
  val name = "Auctioneer"
  def createAuction(item: Comic, startingPrice: Int,
                    duration: FiniteDuration, sender: ActorRef = null) {
	  val uni_name = item.getUniqueName
	  val matchingItems = 
	    (liveAuctions ::: endedAuctions).filter { x => x.name == uni_name }
    if (matchingItems.length == 0) {
    	item.getTopics.foreach { topic => mediator ! Subscribe(topic, self) }
      val ref = context.actorOf(
          Props(new Auction(item, startingPrice, duration)), name=uni_name
      )
  		liveAuctions = AuctionListItem(uni_name, ref) :: liveAuctions
    }
    else if (matchingItems.length > 0 && sender.isInstanceOf[ActorRef])
      sender ! AuctionInProgress
//  else TODO should do something in else statement not sure what 
        
  }
  def endAuction(uni_name: String, ref: ActorRef) = {
    liveAuctions = liveAuctions.filterNot { x => x.name == uni_name }
    endedAuctions = AuctionListItem(uni_name, ref)  :: endedAuctions
    context.stop(ref)
  }
  // Start auctions on first instantiation
  override def preStart(): Unit = {
    for (auctionItem <- auctionItems) {
      createAuction(auctionItem.item, 
                    auctionItem.startingPrice,
                    auctionItem.duration)
    }
  }
  // Override call to preStart which would start new children
  override def postRestart(reason: Throwable): Unit = ()
  override def preRestart(reason: Throwable, message: Option[Any]): Unit = {
    // Overrides call to stop all children and create new ActorRefs
    postStop()
  }
  def receive = {
    case InquireAll    => liveAuctions.foreach(target => target.ref ! Inquire) 
    case Inquire(item) => {
      val target = context.actorSelection(s"${item.getUniqueName}")
      target forward Inquire(item)
    }
    case Bid(amount, item) => {
      val bid_item_name = item.getUniqueName
      val matchingItems = liveAuctions.filter { x => x.name == bid_item_name}
      if (matchingItems.length > 0)
        matchingItems.foreach(target => target.ref forward Bid(amount, item))
      else 
        sender ! AuctionOver
    } 
    case AuctionOver(name, ref) => endAuction(name, ref)
    case AuctionItem(item, startingPrice, duration) => 
      createAuction(item, startingPrice, duration, sender)
    case NewItem(from, msg, topic)  => 
      val direction = if (sender == self) s">> $topic >>" 
                      else s"<< $from $topic <<"
      pprint.pprintln(s"$name $direction $msg")
    case Message(from, msg, topic)  => 
      val direction = if (sender == self) s">> $topic >>" 
                      else s"<< $from $topic <<"
      pprint.pprintln(s"$name $direction $msg")
    case StatusUpdate(item, currentBid, endTime) => 
      pprint.pprintln(StatusUpdate(item, currentBid, endTime))
  }
}


class SomeBidder(item: Comic, bidderName: String) extends Actor {
  var amount = 30
  var bid_closed = 0
  val auctioneer = context.actorSelection("/user/auctioneer")
  val mediator = DistributedPubSub(context.system).mediator
  mediator ! Subscribe(item.superhero, self)
  def receive = {
    case SendBid      => amount += 30; auctioneer ! Bid(amount, item)
    case BestOffer    => pprint.pprintln(s"$bidderName >> Yeah Best Offer!")
    case NotBestOffer => pprint.pprintln(s"$bidderName >> Oh No Not Best!")
    case AuctionOver  =>
      if (bid_closed == 0) {
        pprint.pprintln(s"$bidderName >> Oh right auctions over!")
        bid_closed += 1
      }
    case Winner(item, price) => {
      val winning_message = s"$bidderName >> I won ${item.name} for just $$$price!"
      pprint.pprintln(winning_message)
    }
    case NewItem(from, msg, topic) => 
      pprint.pprintln(s"$bidderName >> Eeee!!! A new ${topic}!!\n")
    //  case _            => TODO need some more default behavior
  }
}


object ComicAuction extends App {
  // Start the cluster
  implicit val system = ActorSystem("ComicAuction")
  import system.dispatcher
  //  val joinAddress = Cluster(system).selfAddress
  //  Cluster(system).join(joinAddress)
  
  // Create actors
  val auctionListener = system.actorOf(Props[AuctionListener],
                                       name="memberListener")
  val comicsForAuction = List(
    AuctionItem(Comic("Amazing Fantasy #15", "Spiderman", 1962, "Stan Lee" ),
                1000,
                5.minutes),
    AuctionItem(Comic("The Amazing Spider-Man #252", "Spiderman",
                      1984, "Randy Schueller" ),
                500, 10.minutes),
    AuctionItem(Comic("The X-Men #1", "Magneto", 1963, "Stan Lee" ),
                200, 2.minutes),
    AuctionItem(Comic("Marvel Super-Heroes vol. 2, #8", "Squirrel Girl",
                      1992, "Steve Ditko" ),
                100, 15.seconds),
    AuctionItem(Comic("More Fun Comics #73", "Aquaman", 1941,
                      "Mort Weisinger"),
                7, 15.seconds),
    AuctionItem(Comic("Radioactive Man 1", "Radioactive Man", 1952,
                      "Steve Vance" ),
                157, 1.minute)
  )
  val auctioneer = system.actorOf(Props(new Auctioneer(comicsForAuction)),
                                  name="auctioneer")
  
  // After zero seconds, send a InquireAll message every 5 seconds to 
  // the auctioneer with a sender of no sender
  // system.scheduler.schedule(
  //   5.seconds, 5.second, auctioneer, InquireAll
  // )(system.dispatcher, Actor.noSender)
  
  // Thread.sleep(5000)
  // Fun with Bill
  val favoriteComic = Comic("Marvel Super-Heroes vol. 2, #8",
                            "Squirrel Girl",1992, "Steve Ditko")
  val bill = system.actorOf(Props(new SomeBidder(favoriteComic, "Bill")),
                            name="Bill")
  system.scheduler.schedule(
       3.seconds, 5.second, bill, SendBid
  )(system.dispatcher, Actor.noSender)

  // Add a new comic to the mix
  Thread.sleep(35000)
  auctioneer ! AuctionItem(Comic("Marvel Super-Heroes vol. 2, #10",
                                 "Squirrel Girl", 1993, "Steve Ditko" ),
                           300, 20.seconds)
}