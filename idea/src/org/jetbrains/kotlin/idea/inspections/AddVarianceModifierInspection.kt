/*
 * Copyright 2010-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.kotlin.idea.inspections

import com.intellij.codeInspection.*
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.kotlin.descriptors.TypeParameterDescriptor
import org.jetbrains.kotlin.diagnostics.DiagnosticSink
import org.jetbrains.kotlin.idea.caches.resolve.analyzeFully
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtTypeParameter
import org.jetbrains.kotlin.psi.KtVisitorVoid
import org.jetbrains.kotlin.psi.addRemoveModifier.addModifier
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.ManualVariance
import org.jetbrains.kotlin.resolve.VarianceCheckerCore
import org.jetbrains.kotlin.types.Variance

class AddVarianceModifierInspection : AbstractKotlinInspection() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean, session: LocalInspectionToolSession): PsiElementVisitor {
        return object : KtVisitorVoid() {

            private fun variancePossible(
                    klass: KtClassOrObject,
                    parameterDescriptor: TypeParameterDescriptor,
                    variance: Variance,
                    context: BindingContext
            ) = VarianceCheckerCore(
                    context,
                    DiagnosticSink.DO_NOTHING,
                    ManualVariance(parameterDescriptor, variance)
            ).checkClassOrObject(klass)

            override fun visitClassOrObject(klass: KtClassOrObject) {
                val context = klass.analyzeFully()
                for (typeParameter in klass.typeParameters) {
                    if (typeParameter.variance != Variance.INVARIANT) continue
                    val parameterDescriptor =
                            context.get(BindingContext.DECLARATION_TO_DESCRIPTOR, typeParameter) as? TypeParameterDescriptor ?: continue
                    val variances = listOf(Variance.IN_VARIANCE, Variance.OUT_VARIANCE).filter {
                        variancePossible(klass, parameterDescriptor, it, context)
                    }
                    if (variances.size == 1) {
                        val suggested = variances.first()
                        val fixes = variances.map { AddVarianceFix(it) }
                        holder.registerProblem(
                                typeParameter,
                                "Type parameter can have $suggested variance",
                                ProblemHighlightType.WEAK_WARNING,
                                *fixes.toTypedArray()
                        )
                    }
                }
            }
        }
    }

    class AddVarianceFix(val variance: Variance) : LocalQuickFix {
        override fun getName() = "Add '$variance' variance"

        override fun getFamilyName() = "Add variance"

        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
            val typeParameter = descriptor.psiElement as? KtTypeParameter
                                ?: throw AssertionError("Add variance fix is used on ${descriptor.psiElement.text}")
            addModifier(typeParameter, if (variance == Variance.IN_VARIANCE) KtTokens.IN_KEYWORD else KtTokens.OUT_KEYWORD)
        }

    }

}