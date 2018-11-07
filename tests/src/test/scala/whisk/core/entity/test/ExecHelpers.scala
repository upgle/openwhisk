/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package whisk.core.entity.test

import org.scalatest.Matchers
import org.scalatest.Suite
import common.StreamLogging
import common.WskActorSystem
import whisk.core.WhiskConfig
import whisk.core.entity._
import whisk.core.entity.ArgNormalizer.trim
import whisk.core.entity.ExecManifest._
import whisk.core.entity.size._
import spray.json._
import spray.json.DefaultJsonProtocol._

trait ExecHelpers extends Matchers with WskActorSystem with StreamLogging {
  self: Suite =>

  private val config = new WhiskConfig(ExecManifest.requiredProperties)
  ExecManifest.initialize(config) should be a 'success

  protected val NODEJS = "nodejs"
  protected val NODEJS6 = "nodejs:6"
  protected val SWIFT = "swift"
  protected val SWIFT3 = "swift:3.1.1"
  protected val BLACKBOX = "blackbox"
  protected val SWIFT3_IMAGE = "action-swift-v3.1.1"
  protected val JAVA_DEFAULT = "java"

  private def attFmt[T: JsonFormat] = Attachments.serdes[T]

  protected def imagename(name: String) = {
    ExecManifest.runtimesManifest.runtimes.flatMap(_.versions).find(_.kind == name).get.image
  }

  protected def jsOld(code: String, main: Option[String] = None) = {
    CodeExecAsString(RuntimeManifest(NODEJS, imagename(NODEJS), deprecated = Some(true)), trim(code), main.map(_.trim))
  }

  protected def js(code: String, main: Option[String] = None) = {
    val attachment = attFmt[String].read(code.trim.toJson)
    val manifest = ExecManifest.runtimesManifest.resolveDefaultRuntime(NODEJS).get

    CodeExecAsAttachment(manifest, attachment, main.map(_.trim), Exec.isBinaryCode(code))
  }

  protected def js6Old(code: String, main: Option[String] = None) = {
    CodeExecAsString(
      RuntimeManifest(
        NODEJS6,
        imagename(NODEJS6),
        default = Some(true),
        deprecated = Some(false),
        stemCells = Some(List(StemCell(2, 256.MB)))),
      trim(code),
      main.map(_.trim))
  }
  protected def js6(code: String, main: Option[String] = None) = {
    val attachment = attFmt[String].read(code.trim.toJson)
    val manifest = ExecManifest.runtimesManifest.resolveDefaultRuntime(NODEJS6).get

    CodeExecAsAttachment(manifest, attachment, main.map(_.trim), Exec.isBinaryCode(code))
  }

  protected def jsDefault(code: String, main: Option[String] = None) = {
    js6(code, main)
  }

  protected def js6MetaDataOld(main: Option[String] = None, binary: Boolean) = {
    CodeExecMetaDataAsString(
      RuntimeManifest(
        NODEJS6,
        imagename(NODEJS6),
        default = Some(true),
        deprecated = Some(false),
        stemCells = Some(List(StemCell(2, 256.MB)))),
      binary,
      main.map(_.trim))
  }

  protected def js6MetaData(main: Option[String] = None, binary: Boolean) = {
    val manifest = ExecManifest.runtimesManifest.resolveDefaultRuntime(NODEJS6).get

    CodeExecMetaDataAsAttachment(manifest, binary, main.map(_.trim))
  }

  protected def javaDefault(code: String, main: Option[String] = None) = {
    val attachment = attFmt[String].read(code.trim.toJson)
    val manifest = ExecManifest.runtimesManifest.resolveDefaultRuntime(JAVA_DEFAULT).get

    CodeExecAsAttachment(manifest, attachment, main.map(_.trim), Exec.isBinaryCode(code))
  }

  protected def javaMetaData(main: Option[String] = None, binary: Boolean) = {
    val manifest = ExecManifest.runtimesManifest.resolveDefaultRuntime(JAVA_DEFAULT).get

    CodeExecMetaDataAsAttachment(manifest, binary, main.map(_.trim))
  }

  protected def swiftOld(code: String, main: Option[String] = None) = {
    CodeExecAsString(RuntimeManifest(SWIFT, imagename(SWIFT), deprecated = Some(true)), trim(code), main.map(_.trim))
  }

  protected def swift(code: String, main: Option[String] = None) = {
    val attachment = attFmt[String].read(code.trim.toJson)
    val manifest = ExecManifest.runtimesManifest.resolveDefaultRuntime(SWIFT).get

    CodeExecAsAttachment(manifest, attachment, main.map(_.trim), Exec.isBinaryCode(code))
  }

  protected def swift3(code: String, main: Option[String] = None) = {
    val attachment = attFmt[String].read(code.trim.toJson)
    val manifest = ExecManifest.runtimesManifest.resolveDefaultRuntime(SWIFT3).get

    CodeExecAsAttachment(manifest, attachment, main.map(_.trim), Exec.isBinaryCode(code))
  }

  protected def sequence(components: Vector[FullyQualifiedEntityName]) = SequenceExec(components)

  protected def sequenceMetaData(components: Vector[FullyQualifiedEntityName]) = SequenceExecMetaData(components)

  protected def bb(image: String) = BlackBoxExec(ExecManifest.ImageName(trim(image)), None, None, false, false)

  protected def bb(image: String, code: String, main: Option[String] = None) = {
    val (codeOpt, binary) =
      if (code.trim.nonEmpty) (Some(attFmt[String].read(code.toJson)), Exec.isBinaryCode(code))
      else (None, false)
    BlackBoxExec(ExecManifest.ImageName(trim(image)), codeOpt, main, false, binary)
  }

  protected def blackBoxMetaData(image: String, main: Option[String] = None, binary: Boolean) = {
    BlackBoxExecMetaData(ExecManifest.ImageName(trim(image)), main, false, binary)
  }
}
