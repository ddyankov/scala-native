package salty
package ir

import scala.collection.mutable
import salty.ir.{Schema => Sc}

// TODO: store offsets in desc
// TODO: ensure that all mutability is private[ir]
sealed class Node private (
              val desc:    Desc,
              val name:    Name,
  private[ir] var slots:   Array[Slot]       = null,
  private[ir] var offsets: Array[Int]        = null,
  private[ir] var epoch:   Int               = 0,
  private[ir] var uses:    mutable.Set[Slot] = mutable.Set.empty
) {
  private[ir] def this(desc: Desc, name: Name) = this(desc, name, Array(), Array())

  private def length(index: Int): Int =
    if (index + 1 < offsets.length)
      offsets(index + 1) - offsets(index)
    else
      slots.length - offsets(index)

  private[ir] def at(index: Int): Slot =
    slots(offsets(index))

  private[ir] def multiAt(index: Int): MultiSlot =
    new MultiSlot(this, length(index), offsets(index))

  final override def toString = s"Node($desc, ...)"

  // TODO: cycles
  final def type_==(other: Node): Boolean =
    (this, other) match {
      case (Extern(), Extern()) =>
        this.name == other.name
      case (Struct(args1), Struct(args2)) =>
        args1.toSeq.zip(args2.toSeq).forall { case (l, r) => l type_== r }
      case (Ptr(arg1), Ptr(arg2)) =>
        arg1 type_== arg2
      case (Function(ret1, args1), Function(ret2, args2)) =>
        (ret1 type_== ret2) && (args1.toSeq.zip(args2.toSeq).forall { case (l, r) => l type_== r })
      case (Slice(arg1), Slice(arg2)) =>
        arg1 type_== arg2
      case _ =>
        this eq other
    }

  // TODO: iterator
  final def edges: Seq[(Sc, Slot)] =
    desc.schema.zipWithIndex.flatMap {
      case (Sc.Many(sc), idx) => multiAt(idx).toSeq.map((sc, _))
      case (sc,          idx) => Seq((sc, at(idx)))
      case _                  => throw new Exception("schema violation")
    }

  final def deps: Iterator[Node] =
    slots.toIterator.map(_.get)
}
object Node {
  private[ir] var lastEpoch = 0
  private[ir] def nextEpoch = {
    lastEpoch += 1
    lastEpoch
  }

  private[ir] def apply(desc: Desc, name: Name = Name.No): Node =
    new Node(desc, name)

  private[ir] def apply(desc: Desc, name: Name, deps: Array[Any] /* Array[Slot | Seq[Slot]] */): Node = {
    val node    = new Node(desc, name)
    val slots   = new mutable.ArrayBuffer[Slot]
    val offsets = new mutable.ArrayBuffer[Int]
    var offset  = 0
    deps.zip(desc.schema).foreach {
      case (seq: Seq[_], Sc.Many(schema)) =>
        seq.asInstanceOf[Seq[Node]].foreach { n =>
          slots += new Slot(schema, node, n)
        }
        offsets += offset
        offset  += seq.length
      case (n: Node, schema) =>
        slots   += new Slot(schema, node, n)
        offsets += offset
        offset  += 1
      case _ =>
        throw new Exception("Schema violation.")
    }
    node.slots   = slots.toArray
    node.offsets = offsets.toArray
    node
  }
}

sealed abstract class Prim(name: Name) extends Node(Desc.Primitive, name)
object Prim {
  final case object Null    extends Prim(Name.Primitive("null"))
  final case object Nothing extends Prim(Name.Primitive("nothing"))
  final case object Unit    extends Prim(Name.Primitive("unit"))
  final case object Bool    extends Prim(Name.Primitive("bool"))

  sealed abstract case class I(width: Int) extends Prim(Name.Primitive(s"i$width"))
  final object I8  extends I(8)
  final object I16 extends I(16)
  final object I32 extends I(32)
  final object I64 extends I(64)

  sealed abstract case class F(width: Int) extends Prim(Name.Primitive(s"f$width"))
  final object F32 extends F(32)
  final object F64 extends F(64)

  final object Object extends Prim(Name.Class("java.lang.Object")) {
    def resolve(name: Name): Option[Node] = name match {
      case `initName`      => Some(initMethod)
      case `cloneName`     => Some(cloneMethod)
      case `equalsName`    => Some(equalsMethod)
      case `finalizeName`  => Some(finalizeMethod)
      case `getClassName`  => Some(getClassMethod)
      case `hashCodeName`  => Some(hashCodeMethod)
      case `notifyName`    => Some(notifyMethod)
      case `notifyAllName` => Some(notifyAllMethod)
      case `toStringName`  => Some(toStringMethod)
      case `wait0Name`     => Some(wait0Method)
      case `wait1Name`     => Some(wait1Method)
      case `wait2Name`     => Some(wait2Method)
      case _               => None
    }

    lazy val initName: Name = Name.Constructor(name, Seq())
    lazy val initMethod: Node =
      Method(Unit, Seq(), End(Seq(Return(Empty, Empty, Unit))), this, name = initName)

    lazy val cloneName: Name = Name.Method(name, "clone", Seq(), name)
    lazy val cloneMethod: Node = ???

    lazy val equalsName: Name = Name.Method(name, "equals", Seq(name), Bool.name)
    lazy val equalsMethod: Node = ???

    lazy val finalizeName: Name = Name.Method(name, "finalize", Seq(), Unit.name)
    lazy val finalizeMethod: Node = ???

    lazy val getClassName: Name = Name.Method(name, "getClass", Seq(), Name.Class("java.lang.Class"))
    lazy val getClassMethod: Node = ???

    lazy val hashCodeName: Name = Name.Method(name, "hashCode", Seq(), I32.name)
    lazy val hashCodeMethod: Node = ???

    lazy val notifyName: Name = Name.Method(name, "notify", Seq(), Unit.name)
    lazy val notifyMethod: Node = ???

    lazy val notifyAllName: Name = Name.Method(name, "notifyAll", Seq(), Unit.name)
    lazy val notifyAllMethod: Node = ???

    lazy val toStringName: Name = Name.Method(name, "toString", Seq(), Name.Class("java.lang.String"))
    lazy val toStringMethod: Node = ???

    lazy val wait0Name: Name = Name.Method(name, "wait", Seq(), Unit.name)
    lazy val wait0Method: Node = ???

    lazy val wait1Name: Name = Name.Method(name, "wait", Seq(I64.name), Unit.name)
    lazy val wait1Method: Node = ???

    lazy val wait2Name: Name = Name.Method(name, "wait", Seq(I64.name, I32.name), Unit.name)
    lazy val wait2Method: Node = ???
  }
}

final case object Empty extends Node(Desc.Empty, Name.No)
