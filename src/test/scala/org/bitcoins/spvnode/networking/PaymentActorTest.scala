package org.bitcoins.spvnode.networking

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestActorRef, TestKit}
import org.bitcoins.core.crypto.{DoubleSha256Digest, Sha256Hash160Digest}
import org.bitcoins.core.number.UInt32
import org.bitcoins.core.protocol.P2PKHAddress
import org.bitcoins.core.protocol.blockchain.BlockHeader
import org.bitcoins.core.protocol.transaction.Transaction
import org.bitcoins.core.util.{Leaf, Node}
import org.bitcoins.spvnode.block.{MerkleBlock, PartialMerkleTree}
import org.bitcoins.spvnode.constant.Constants
import org.bitcoins.spvnode.messages.{MsgBlock, MsgTx}
import org.bitcoins.spvnode.messages.data.{Inventory, InventoryMessage, MerkleBlockMessage, TransactionMessage}
import org.scalatest._

import scala.concurrent.duration.DurationInt

/**
  * Created by chris on 9/1/16.
  */
class PaymentActorTest extends TestKit(ActorSystem("MySpec")) with ImplicitSender
  with FlatSpecLike with MustMatchers with BeforeAndAfterAll {

  val txId = DoubleSha256Digest("0d507a29efb362ce93687f524e7e3a668689e335ba20374c93710efdf7597c5f")
  val transaction = Transaction("0100000001f78d02e5d2e37319a4cec31331babea9f0c6b9efb75060e27cf23997c6e560b3010000006a47304402207f6d19701c0e58bdedbc5073c17ac36e3493326c8c916db7dd224961fa8c8c9f02201ba78149c12a9754f7ceab1bcfe4c6afb8fb5ee38078f47065d316cddaa932b40121023de7008d781aa60ed8b0cdf92ece1d3e6eca2a0fd958d883114129a450ab05f2feffffff02bf9fb700000000001976a914a82d2cefa38fe32eb90c5d31d2063dde716c90df88ac009f2400000000001976a914415a05d63df2c212e1c750a70eba49d6d8af196d88accb210e00")
  "PaymentActor" must "monitor an address, then send SuccessfulPayment or FailedPayment message if that address is not paid in the next block" in {
    val paymentActor = TestActorRef(PaymentActor.props,self)
    val pubKeyHash = Sha256Hash160Digest("415a05d63df2c212e1c750a70eba49d6d8af196d")
    val addr = P2PKHAddress(pubKeyHash,Constants.networkParameters)
    paymentActor ! addr

    //TODO: Remove this thread.sleep call
    //wait for connection to be made so we have the right context
    Thread.sleep(3000)
    //build an inventory message, then send it to the payment actor
    val inventory = Inventory(MsgTx, txId)
    val txIdInvMsg = InventoryMessage(Seq(inventory))
    paymentActor ! txIdInvMsg

    //now the payment actor switches to waiting for the full transaction
    //so send the actor the full transaction
    val txMsg = TransactionMessage(transaction)
    paymentActor ! txMsg

    //after seeing the tx message, our payment actor waits for a block to be announced on the network
    val blockMsg = Inventory(MsgBlock, DoubleSha256Digest("62862488a791bf863ea840f8b9e4ded91ef5625e73b4f56940d6050000000000"))
    val blockInvMsg = InventoryMessage(Seq(blockMsg))
    paymentActor ! blockInvMsg


    val partialMerkleTree = PartialMerkleTree(UInt32(36),List(DoubleSha256Digest("27f706c39b2ea48d9316d85f513080da35329f3629ecf5f22869e191d38f3553"),
      DoubleSha256Digest("0d507a29efb362ce93687f524e7e3a668689e335ba20374c93710efdf7597c5f"),
      DoubleSha256Digest("b80117bee395e816a26e807dcb5858403142dcb8d5edfc3eaa6dde700a9198a2"),
      DoubleSha256Digest("d297f7e4e712967f77f87c65fc698fc6ff8fc0fb056b07ebd459567d0a1c36f8"),
      DoubleSha256Digest("114b915455ad5cb314e77c648e243f71d9b4895ab96c38cc3c7e27fd151d112b"),
      DoubleSha256Digest("c83ce4bd870c2d791d73d1ce3fd7b96f61c94d3ce3af270af22938c0d15b683a"),
      DoubleSha256Digest("ec89457fd619020e11727f01d7f5518b7c3114aaa70376611efe9efd41c5c099")),
      List(true, true, false, true, true, true, true, true, false, false, false, false, false, false, false, false)
    )
    //after seeing a new block announcement on the network we request a merkle block message from the peer on the network
    //this merkle block message is taken from a node on the network
    val merkleBlockMsg = MerkleBlockMessage(MerkleBlock(BlockHeader(UInt32(805306368),
      DoubleSha256Digest("1d73fa2ffbdf79c2e78e3312066833c4a264a19b958faf450100000000000000"),
      DoubleSha256Digest("9b47cf5d64aa52d7536e2b469891a79ea8488092dc3c2e0ed26dbe9b508cce16"),
      UInt32(1472661981),UInt32(486604799),UInt32(4219144207L)),UInt32(36),
      partialMerkleTree))
    paymentActor ! merkleBlockMsg
    expectMsgType[PaymentActor.SuccessfulPayment](10.seconds)
  }


}
