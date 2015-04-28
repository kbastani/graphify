package traversal

import java.{lang, util}

import com.github.mdr.ascii.layout.GraphLayout
import com.github.mdr.ascii.layout.prefs.LayoutPrefsImpl
import org.neo4j.graphdb.GraphDatabaseService
import org.neo4j.nlp.helpers.GraphManager
import org.neo4j.nlp.impl.manager.NodeManager

import scala.collection.{JavaConversions, mutable}
import scala.util.matching.Regex
/**
 * Copyright (C) 2014 Kenny Bastani
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
/**
 * The [[DecisionTree]] provides a mechanism for planning the optimal shortest path from a source vertex to a destination vertex
 * @param root is the id of the vertex that is at the base of this [[DecisionTree]]
 * @tparam VD is the vertex id type for the decision tree
 */
class DecisionTree[VD](val root : VD, var graph : mutable.HashMap[VD, DecisionTree[VD]], db: GraphDatabaseService, graphManager: GraphManager) extends Serializable {

  var matchCount = 0

  /**
   * The branches for this decision tree, consisting of a set of leaf vertices that have context to a traversable branch
   */
  var branches: scala.collection.mutable.SynchronizedSet[DecisionTree[VD]] = new scala.collection.mutable.HashSet[DecisionTree[VD]]() with mutable.SynchronizedSet[DecisionTree[VD]]

  if(!graph.contains(root))
    addNode(this)

  var distance: Int = -1

  def cloneTree(newGraph: mutable.HashMap[VD, DecisionTree[VD]]): DecisionTree[VD] = {
    val cloned = new DecisionTree[VD](root, newGraph, db, graphManager)

    // Clone all branches
    cloned.branches.++=(for (tree <- branches) yield {
      newGraph.getOrElseUpdate(tree.root, tree)
      tree.cloneTree(newGraph)
    })

    cloned
  }

  override def clone(): DecisionTree[VD] = {
    val cloned = new DecisionTree[VD](root, graph.clone(), db, graphManager)

    // Clone all branches
    cloned.branches.++=(for (tree <- branches) yield {
      tree.clone()
    })

    cloned
  }

  def addBranch(branch: DecisionTree[VD]): DecisionTree[VD] = {
    addBranches(branch.branches)

    branch
  }

  def addBranches(branches: scala.collection.mutable.SynchronizedSet[DecisionTree[VD]]) = {

    for (branch <- branches) yield {
      // Get or put the decision tree into the global decision tree hash map
      val treeBranch = graph.getOrElse(branch.root, null)

      // Recursively update the graph
      if (treeBranch == null) {
        val newBranch: DecisionTree[VD] = branch.clone()
        newBranch.graph = this.graph
        this.graph synchronized {
          this.graph.put(newBranch.root, newBranch)
        }

        this.addLeaf(newBranch.root)

        branch.branches.foreach(a => addBranch(a))
      } else {
        treeBranch.addBranch(branch)
        // Update tree branch
        this.addLeaf(treeBranch.root)
      }

      branch
    }

  }

  /**
   * Adds a leaf vertex to this branch
   * @param leaf is the vertex id of the new leaf
   * @return a new branch for this leaf
   */
  def addLeaf(leaf: VD): DecisionTree[VD] = {
    // Get or put the decision tree into the global decision tree hash map
    val branch = graph.getOrElseUpdate(leaf, synchronized {
      new DecisionTree[VD](leaf, graph, db, graphManager)
    })
    this.branches.add(branch)
    branch
  }

  def addNode(leaf: DecisionTree[VD]): DecisionTree[VD] = {
    // Get or put the decision tree into the global decision tree hash map
    val branch = graph.getOrElseUpdate(leaf.root, this)
    branch
  }

  def addNode(leaf: VD): DecisionTree[VD] = {
    // Get or put the decision tree into the global decision tree hash map
    val branch = graph.getOrElseUpdate(leaf, synchronized {
      new DecisionTree[VD](leaf, graph, db, graphManager)
    })
    branch
  }

  def getNode(leaf: VD): DecisionTree[VD] = {
    // Get or put the decision tree into the global decision tree hash map
    val branch = graph.getOrElse(leaf, null)
    branch
  }

  def getProperties(): Map[String, Object] = {
    val mapResult = JavaConversions.mapAsScalaMap[String, Object](NodeManager.getNodeAsMap(root.asInstanceOf[Long], db)).toMap
    mapResult
  }

  def traverseByPattern(item: VD, input: String, seq: Seq[VD], depth: Int): Array[Seq[(VD, Int)]] = {

    // Lazily load the branches from cache
    val thisBranch = loadBranches(input)

    val thisBranches = thisBranch.branches.map(a => a.matchPattern(input)).seq

    //val matchedChildren = thisBranches.filter(b => b.matchCount > 0 && seq.find(a => a.equals(b.root)).getOrElse(null) == null)

    if (thisBranches.size == 0) {
      return Array(Seq[(VD, Int)]((this.root, matchPattern(input).matchCount)))
    }

    val result = {
      if (thisBranch.branches.size > 0) {
        for (branch <- thisBranches;
             x = branch.traverseByPattern(root, input, seq ++ Seq[VD](this.root), depth + 1)
             if x != null)
          yield x
      } else {
        null
      }
    }

    result match {
      case x: mutable.HashSet[Array[Seq[(VD, Int)]]] => {
        result.flatMap(a => a).map(a => Seq[(VD, Int)]((this.root, matchPattern(input).matchCount)) ++ a).toArray
      }
      case x => null

    }
  }

  def loadBranches() : DecisionTree[VD] = {
    val rels: util.List[lang.Long] = graphManager.getRelationships(root.asInstanceOf[Long], db)
    JavaConversions.asScalaIterator(
      rels.iterator()).toSeq.par.foreach {
      a => {
        this.addNode(root).addLeaf(a.asInstanceOf[VD])
      }
    }
    graph.get(root).getOrElse(this)
  }

  def loadBranches(input : String) : DecisionTree[VD] = {
    val patterns = graphManager.getPatternMatchers(root.asInstanceOf[Long], db)

    val matchedPatterns = JavaConversions.collectionAsScalaIterable(patterns).seq
      .filter(a => matchPattern(input, a))

    matchedPatterns.map(a => graphManager.getNodeIdFromCache(a, db)).toSeq
      .foreach {
      a => {
        this.addNode(root).addLeaf(a.asInstanceOf[VD])
      }
    }

    graph.get(root).getOrElse(this)
  }


  def matchPattern(input: String, regex: String): Boolean = {
    // Get pattern and attempt to match
    val pattern = String.format("(?i)(%s)+", regex)
      new Regex(pattern, pattern).pattern.matcher(input).find()
  }

  def matchPattern(input: String): DecisionTree[VD] = {
    // Get pattern and attempt to match
    val pattern = String.format("(?i)(%s)+", getProperties().get("pattern").get.toString)
    val matcher = new Regex(pattern).pattern.matcher(input)

    var matcherCount: Int = 0
    while (matcher.find) {
      matcherCount += 1
    }

    this.matchCount = matcherCount

    this
  }

  def traverseByPattern(input: String): util.Map[VD, Integer] = {
    JavaConversions.mapAsJavaMap[VD, Integer](traverseByPattern(root, input, Seq[VD](), 0)
      .toSeq.par.flatMap(a => a).map(a => (a._1, Integer.valueOf(a._2))).seq.toMap[VD, Integer])
  }

  def traverseTo(item: VD): DecisionTree[VD] = {
    traverseTo(item, Seq[VD]())
  }


  /**
   * Traverses the decision tree until it finds a branch that matches a supplied vertex id
   * @param item is the vertex id of the item to traverse to
   * @return a branch for the desired vertex
   */
  def traverseTo(item: VD, seq: Seq[VD]): DecisionTree[VD] = {

    val thisBranch = this.loadBranches()


    if (seq.contains(this.root)) {
      return null
    }

    if (item == root) {
      if (seq.contains(this.root)) {
        return null
      }

      this
    } else {
      val result = {
        if (seq.find(a => a.equals(item)).getOrElse(null) == null) {
          for (branch <- thisBranch.branches; x = branch.traverseTo(item, seq ++ Seq[VD](this.root)) if x != null && !seq.contains(x)) yield x
        } else {
          Seq[DecisionTree[VD]]()
        }
      }.take(1)
        .find(a => a != null)
        .getOrElse(null)

      if (seq.contains(this.root)) {
        return null
      }

      result
    }
  }

  def shortestPathTo(item: VD): Seq[VD] = {
    shortestPathTo(item, Seq[VD]())
  }

  /**
   * Gets the shortest path to the item or else returns null
   * @param item is the id of the vertex to traverse to
   * @return the shortest path as a sequence of [[VD]]
   */
  def shortestPathTo(item: VD, seq: Seq[VD]): Seq[VD] = {

    val thisBranch = this.loadBranches()

    if (seq.contains(this.root)) {
      return null
    }

    if (item == root) {
      if (seq.contains(this.root)) {
        return null
      }
      Seq[VD](this.root)
    } else {
      val result = {
        for (branch <- thisBranch.branches; x = branch.shortestPathTo(item, seq ++ Seq[VD](this.root)) if x != null && !seq.contains(x)) yield x
      }.toSeq.sortBy(b => b.length).take(1)
        .find(a => a != null)
        .getOrElse(null)

      if (seq.contains(this.root)) {
        return null
      }

      result match {
        case x: Seq[VD] => x.+:(root)
        case x => null
      }
    }
  }

  object branchOrdering extends Ordering[DecisionTree[VD]] {
    def compare(a: DecisionTree[VD], b: DecisionTree[VD]) = a.distance compare b.distance
  }

  def allShortestPathsTo(item: VD, distance: Int, depth: Int): Array[Seq[VD]] = {
    if (depth > distance) return null

    if (item == root) {
      Array(Seq[VD](this.root))
    } else {
      val result = {
        if (branches.size > 0) {
          val minV = branches.min(branchOrdering)
          for (branch <- branches.filter(b => b.distance == minV.distance); x = branch.allShortestPathsTo(item, distance, depth + 1) if x != null) yield x
        } else {
          null
        }
      }

      result match {
        case x: scala.collection.mutable.HashSet[Array[Seq[VD]]] => {
          result.flatMap(a => a).map(a => Seq[VD](this.root) ++ a).toArray
        }
        case x => null
      }
    }
  }

  def allShortestPathsTo(item: VD): Seq[Seq[VD]] = {
    allShortestPaths(item, 1, 0)
  }

  def allShortestPaths(item: VD, minDistance: Int, maxDistance: Int): Seq[Seq[VD]] = {
    val shortestPath = {
      if (distance == -1) {
        return null
      } else {
        distance
      }
    }

    if (shortestPath > minDistance) {
      val thisShortestPath = allShortestPathsTo(item, shortestPath, 0)
      if (thisShortestPath.length > 0) {
        thisShortestPath
      } else {
        null
      }
    } else {
      null
    }
  }

  def toGraph: com.github.mdr.ascii.graph.Graph[String] = {
    toGraph(Seq[VD](), scala.collection.mutable.Set[(String, String)](), scala.collection.mutable.Set[String]())
  }

  /**
   * Converts a [[DecisionTree]] to a [[com.github.mdr.ascii.graph.Graph]] which can be rendered in ASCII art
   * @return a [[com.github.mdr.ascii.graph.Graph]] that can be visualized as ASCII art
   */
  def toGraph(seq: Seq[VD], edges: scala.collection.mutable.Set[(String, String)], vertices: scala.collection.mutable.Set[String]): com.github.mdr.ascii.graph.Graph[String] = {
    vertices.add(root.toString)
    branches.foreach(a => if (!vertices.contains(a.root.toString)) vertices.add(a.root.toString))
    branches.map(a => (root.toString, a.root.toString)).foreach(a => edges.add(a))
    val thisBranches = branches

    thisBranches.foreach(a => {
      if (!seq.contains(a.root)) {
        val thisGraph = a.toGraph(seq ++ Seq[VD](a.root), edges, vertices)
        thisGraph.vertices.filter(b => b != a.root).foreach(b => vertices.add(b))
        thisGraph.edges.foreach(b => edges.add(b))
      }
    })

    com.github.mdr.ascii.graph.Graph(vertices.toList.toSet, edges.toList)
  }

  /**
   * Renders the [[DecisionTree]] in ASCII art
   * @return a [[String]] that has a graph layout visualization in ASCII art of the [[DecisionTree]]
   */
  def renderGraph: String = {
    val layoutPrefs = LayoutPrefsImpl(unicode = true,
      explicitAsciiBends = true,
      compactify = false,
      removeKinks = true,
      vertical = false,
      doubleVertices = false,
      rounded = false)

    "\n" + GraphLayout.renderGraph(this.toGraph, layoutPrefs = layoutPrefs)
  }
}