package org.statismo.stk.ui

import org.statismo.stk.core.geometry.Point3D
import java.awt.Color
import breeze.linalg.DenseVector
import scala.swing.Publisher
import scala.swing.event.Event
import java.io.File
import scala.util.Try
import org.statismo.stk.core.io.LandmarkIO
import org.statismo.stk.core.geometry.ThreeD

trait Landmark extends Nameable with Removeable {
  def peer: Point3D
}

class ReferenceLandmark(val peer: Point3D) extends Landmark {
  override def remove = {
    super.remove
  }
}

class DisplayableLandmark(container: DisplayableLandmarks) extends Landmark with Displayable with SphereLike {
  override lazy val parent = container
  color = container.color
  opacity = container.opacity
  radius = container.radius
  def peer = center
}

class StaticLandmark(initialCenter: Point3D, container: StaticLandmarks) extends DisplayableLandmark(container) {
  center = initialCenter
}

class MoveableLandmark(container: MoveableLandmarks, source: ReferenceLandmark) extends DisplayableLandmark(container) {
  override lazy val parent = container

  name = source.name
  listenTo(container.instance.meshRepresentation, source)

  override def remove = {
    // we simply forward the request to the source, which in turn publishes an event that all attached
    // moveable landmarks get. And only then we invoke the actual remove functionality (in the reactions below)
	source.remove
  }

  reactions += {
    case Mesh.GeometryChanged(m) => setCenter
    case Nameable.NameChanged(n) => {
      if (n == source) {
        this.name = source.name
      } else if (n == this) {
        source.name = this.name
      }
    }
    case Removeable.Removed(r) => {
      if (r eq source) {
        parent.remove(this, true)
      }
    }
  }

  setCenter

  def setCenter = {
    val mesh = container.instance.meshRepresentation.triangleMesh
    val coeffs = DenseVector(container.instance.coefficients.toArray)
    val mappedPt = source.peer + container.instance.model.gp.instance(coeffs)(source.peer)
    center = mappedPt
  }

}

object Landmarks extends FileIoMetadata {
  case class LandmarksChanged(source: AnyRef) extends Event
  override val description = "Landmarks"
  override val fileExtensions = Seq("csv")
}

trait Landmarks[L <: Landmark] extends MutableObjectContainer[L] with EdtPublisher with Saveable with Loadable {
  val saveableMetadata = Landmarks
  val loadableMetadata = Landmarks

  override def isCurrentlySaveable: Boolean = !children.isEmpty

  def create(peer: Point3D, name: Option[String]): Unit

  override def addAll(lms: Seq[L]): Unit = {
    super.addAll(lms)
    publish(Landmarks.LandmarksChanged(this))
  }

  override def remove(lm: L) = {
    val changed = super.remove(lm)
    if (changed) publish(Landmarks.LandmarksChanged(this))
    changed
  }

  override def saveToFile(file: File): Try[Unit] = {
    val seq = children.map { lm => (lm.name, lm.peer) }.toIndexedSeq
    LandmarkIO.writeLandmarks[ThreeD](file, seq)
  }

  override def loadFromFile(file: File): Try[Unit] = {
    Try {this.removeAll}
    for {
      saved <- LandmarkIO.readLandmarks3D(file)
      val newLandmarks = {
        saved.map {
          case (name, point) =>
            this.create(point, Some(name))
        }
      }
    } yield {}
  }
}

abstract class DisplayableLandmarks(theObject: ThreeDObject) extends SceneTreeObjectContainer[DisplayableLandmark] with Landmarks[DisplayableLandmark] with Radius with Colorable with RemoveableChildren {
  name = "Landmarks"
  override lazy val isNameUserModifiable = false
  override lazy val parent = theObject
  def addAt(position: Point3D)

  color = Color.BLUE
  opacity = 0.8
  radius = 3.0f

  override def opacity_=(newOpacity: Double) {
    super.opacity_=(newOpacity)
    children.foreach { c =>
      c.opacity = newOpacity
    }
  }

  override def color_=(newColor: Color) {
    super.color_=(newColor)
    children.foreach { c =>
      c.color = newColor
    }
  }

  override def radius_=(newRadius: Float) {
    super.radius_=(newRadius)
    children.foreach { c =>
      c.radius = newRadius
    }
  }

}

class ReferenceLandmarks(val shapeModel: ShapeModel) extends Landmarks[ReferenceLandmark] {
  lazy val nameGenerator: NameGenerator = NameGenerator.defaultGenerator
  def create(template: ReferenceLandmark): Unit = {
    create(template.peer, Some(template.name))
  }
  def create(peer: Point3D, name: Option[String] = None): Unit = {
    val lm = new ReferenceLandmark(peer)
    lm.name = name.getOrElse(nameGenerator.nextName)
    add(lm)
  }
}

class StaticLandmarks(theObject: ThreeDObject) extends DisplayableLandmarks(theObject) {
  lazy val nameGenerator: NameGenerator = NameGenerator.defaultGenerator

  def addAt(peer: Point3D) = create(peer)

  def create(peer: Point3D, name: Option[String] = None): Unit = {
    val lm = new StaticLandmark(peer, this)
    lm.name = name.getOrElse(nameGenerator.nextName)
    add(lm)
  }

}

class MoveableLandmarks(val instance: ShapeModelInstance) extends DisplayableLandmarks(instance) {
  val ref = instance.parent.parent.landmarks

  def addAt(peer: Point3D) = {
    create(peer, None)
  }

  def create(peer: Point3D, name: Option[String]): Unit = {
    val index = instance.meshRepresentation.triangleMesh.findClosestPoint(peer)._2
    val refPoint = instance.model.peer.mesh.points(index).asInstanceOf[Point3D]
    instance.model.landmarks.create(refPoint, name)
  }

  listenTo(ref)

  reactions += {
    case Landmarks.LandmarksChanged(s) => {
      if (s eq ref) {
        syncWithModel
      }
    }
  }

  syncWithModel

  def syncWithModel = {
    var changed = false
    _children.length until ref.children.length foreach { i =>
      changed = true
      add(new MoveableLandmark(this, ref(i)))
    }
    if (changed) {
      publish(SceneTreeObject.ChildrenChanged(this))
    }
  }
}