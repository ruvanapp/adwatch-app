package com.adwatch.backend.routes

import com.adwatch.backend.domain.request.ReferralActionRequest
import com.adwatch.backend.domain.request.ReferralProgramSettingsRequest
import com.adwatch.backend.domain.response.ApiResponse
import com.adwatch.backend.service.ReferralProgramSettingsData
import com.adwatch.backend.service.ReferralService
import com.adwatch.backend.service.SecurityGuards
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.referralRoutes() {
    authenticate("dev-auth", "firebase-auth") {
        route("/referrals") {
            get("/me") {
                try {
                    val ctx = SecurityGuards.extractUserContext(call)
                    val summary = ReferralService.getInviteSummary(ctx.userId)
                    call.respond(HttpStatusCode.OK, ApiResponse(success = true, data = summary))
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, ApiResponse<Unit>(success = false, error = "Failed to load referral summary"))
                }
            }

            get("/relationships") {
                try {
                    val ctx = SecurityGuards.extractUserContext(call)
                    val items = ReferralService.getReferralRelationships(ctx.userId)
                    call.respond(HttpStatusCode.OK, ApiResponse(success = true, data = mapOf("relationships" to items)))
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, ApiResponse<Unit>(success = false, error = "Failed to load referrals"))
                }
            }

            get("/leaderboard") {
                try {
                    val leaderboard = ReferralService.topInviters()
                    call.respond(HttpStatusCode.OK, ApiResponse(success = true, data = mapOf("leaders" to leaderboard)))
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, ApiResponse<Unit>(success = false, error = "Failed to load leaderboard"))
                }
            }
        }
    }

    authenticate("admin-auth") {
        route("/admin/referrals") {
            get {
                try {
                    val overview = ReferralService.adminOverview()
                    val relationships = ReferralService.adminRelationships()
                    val leaders = ReferralService.topInviters()
                    call.respond(
                        HttpStatusCode.OK,
                        ApiResponse(
                            success = true,
                            data = mapOf(
                                "overview" to overview,
                                "relationships" to relationships,
                                "leaders" to leaders
                            )
                        )
                    )
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, ApiResponse<Unit>(success = false, error = "Failed to load admin referrals"))
                }
            }

            get("/settings") {
                try {
                    val settings = ReferralService.getProgramSettings()
                    call.respond(HttpStatusCode.OK, ApiResponse(success = true, data = settings))
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, ApiResponse<Unit>(success = false, error = "Failed to load referral settings"))
                }
            }

            post("/settings") {
                try {
                    val payload = call.receive<ReferralProgramSettingsRequest>()
                    ReferralService.updateProgramSettings(
                        ReferralProgramSettingsData(
                            rewardAmountCredits = payload.rewardAmountCredits,
                            percentageCommission = payload.percentageCommission,
                            minimumActivityCredits = payload.minimumActivityCredits,
                            dailyReferralLimit = payload.dailyReferralLimit,
                            fraudBlockSameDevice = payload.fraudBlockSameDevice,
                            fraudBlockSameIp = payload.fraudBlockSameIp,
                            fraudBlockEmulator = payload.fraudBlockEmulator,
                            fraudBlockMultipleAccounts = payload.fraudBlockMultipleAccounts,
                            active = payload.active
                        )
                    )
                    call.respond(HttpStatusCode.OK, ApiResponse(success = true, data = mapOf("message" to "Referral settings updated")))
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, ApiResponse<Unit>(success = false, error = "Failed to update referral settings"))
                }
            }

            post("/{id}/approve") {
                try {
                    val id = call.parameters["id"] ?: return@post call.respond(HttpStatusCode.BadRequest, ApiResponse<Unit>(success = false, error = "Missing referral id"))
                    ReferralService.approveRelationship(id)
                    call.respond(HttpStatusCode.OK, ApiResponse(success = true, data = mapOf("message" to "Referral approved")))
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, ApiResponse<Unit>(success = false, error = "Failed to approve referral"))
                }
            }

            post("/{id}/reject") {
                try {
                    val id = call.parameters["id"] ?: return@post call.respond(HttpStatusCode.BadRequest, ApiResponse<Unit>(success = false, error = "Missing referral id"))
                    val payload = call.receive<ReferralActionRequest>()
                    ReferralService.rejectRelationship(id, payload.notes)
                    call.respond(HttpStatusCode.OK, ApiResponse(success = true, data = mapOf("message" to "Referral rejected")))
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, ApiResponse<Unit>(success = false, error = "Failed to reject referral"))
                }
            }
        }
    }
}