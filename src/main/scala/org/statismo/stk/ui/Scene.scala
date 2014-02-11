package org.statismo.stk.ui

import scala.collection.immutable.List
import scala.swing.Publisher
import scala.swing.Reactor
import scala.swing.event.Event
import java.util.Vector
import scala.util.Try


object Scene {
  case class TreeTopologyChanged(scene: Scene) extends Event
  case class PerspectiveChanged(scene: Scene) extends Event
  case class VisibilityChanged(scene: Scene) extends Event
}

class Scene extends SceneTreeObject {
  org.statismo.stk.core.initialize

  name = "Scene"
  override lazy val isNameUserModifiable = false

  override implicit lazy val parent = this
  val models = new ShapeModels
  val statics = new StaticThreeDObjects
  val auxiliaries = new AuxiliaryObjects
  
  override val children = List(models, statics)//, auxiliaries)

  def load(paths: String*): Seq[SceneTreeObject] = {
    tryLoad(Seq(paths).flatten).filter(_.isSuccess).map(_.get)
  }

  def tryLoad(paths: Seq[String], factories: Seq[SceneTreeObjectFactory[SceneTreeObject]] = SceneTreeObjectFactory.DefaultFactories): Seq[Try[SceneTreeObject]] = {
    paths.map(fn => SceneTreeObjectFactory.load(fn, factories))
  }

  deafTo(this)
  reactions += {
    case SceneTreeObject.VisibilityChanged(s) => {
      publish(Scene.VisibilityChanged(this))
    }
    case SceneTreeObject.ChildrenChanged(s) => {
      publish(Scene.TreeTopologyChanged(this))
    }
    case m@Nameable.NameChanged(s) => {
      publish(m)
    }
  }
  
  private var _perspective: Perspective = Perspective.defaultPerspective(this)
  def perspective = _perspective
  def perspective_=(newPerspective: Perspective) = {
    _perspective = newPerspective
    publish(Scene.PerspectiveChanged(this))
  }
  
  def viewports = perspective.viewports
}

class AuxiliaryObjects()(implicit override val scene: Scene) extends SceneTreeObjectContainer[Displayable] {
  name = "Auxiliary Objects"
  override lazy val isNameUserModifiable = false
  override lazy val parent = scene
}
