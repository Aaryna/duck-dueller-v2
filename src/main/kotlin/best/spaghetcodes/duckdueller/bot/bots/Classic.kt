package best.spaghetcodes.duckdueller.bot.bots

import best.spaghetcodes.duckdueller.DuckDueller
import best.spaghetcodes.duckdueller.bot.BotBase
import best.spaghetcodes.duckdueller.bot.features.Bow
import best.spaghetcodes.duckdueller.bot.features.MovePriority
import best.spaghetcodes.duckdueller.bot.features.Rod
import best.spaghetcodes.duckdueller.bot.player.Combat
import best.spaghetcodes.duckdueller.bot.player.Inventory
import best.spaghetcodes.duckdueller.bot.player.Mouse
import best.spaghetcodes.duckdueller.bot.player.Movement
import best.spaghetcodes.duckdueller.utils.*
import net.minecraft.init.Blocks
import net.minecraft.util.Vec3
import net.minecraft.util.BlockPos
import net.minecraft.util.EnumFacing

class Classic : BotBase("/play duels_blitz_duel"), Bow, Rod, MovePriority {

    override fun getName(): String {
        return "Classic"
    }

    init {
        setStatKeys(
            mapOf(
                "wins" to "player.stats.Duels.blitz_duel_wins",
                "losses" to "player.stats.Duels.blitz_duel_losses",
                "ws" to "player.stats.Duels.current_blitz_winstreak",
            )
        )
    }

    var shotsFired = 0
    var maxArrows = 5
    var lastRodTime = 0L
    var rodCooldown = 300L // Reduced from ~500ms to 300ms for more spam
    var lastBlockBreakTime = 0L
    var blockBreakCooldown = 1000L
    var lastBlockPlaceTime = 0L
    var blockPlaceCooldown = 800L

    override fun onGameStart() {
        Movement.startSprinting()
        Movement.startForward()
        TimeUtils.setTimeout(Movement::startJumping, RandomUtils.randomIntInRange(400, 1200))
        lastRodTime = 0L
        lastBlockBreakTime = 0L
        lastBlockPlaceTime = 0L
    }

    override fun onGameEnd() {
        shotsFired = 0
        Mouse.stopLeftAC()
        val i = TimeUtils.setInterval(Mouse::stopLeftAC, 100, 100)
        TimeUtils.setTimeout(fun () {
            i?.cancel()
            Mouse.stopTracking()
            Movement.clearAll()
            Combat.stopRandomStrafe()
        }, RandomUtils.randomIntInRange(200, 400))
    }

    var tapping = false

    override fun onAttack() {
        val distance = EntityUtils.getDistanceNoY(mc.thePlayer, opponent())
        if (distance < 3) {
            if (mc.thePlayer != null && mc.thePlayer.heldItem != null) {
                val n = mc.thePlayer.heldItem.unlocalizedName.lowercase()
                if (n.contains("rod")) {
                    Combat.wTap(300)
                    tapping = true
                    combo--
                    TimeUtils.setTimeout(fun () {
                        tapping = false
                    }, 300)
                } else if (n.contains("sword")) {
                    Mouse.rClick(RandomUtils.randomIntInRange(80, 100))
                }
            }
        } else {
            Combat.wTap(100)
            tapping = true
            TimeUtils.setTimeout(fun () {
                tapping = false
            }, 100)
        }
        if (combo >= 3) {
            Movement.clearLeftRight()
        }
    }

    // Enhanced rod spam logic
    private fun shouldUseRod(distance: Double): Boolean {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastRodTime < rodCooldown) return false
        if (Mouse.isUsingProjectile()) return false
        if (opponent() == null) return false
        
        // Aggressive rod spam at expanded ranges
        val inMainRange = distance in 3.0..11.0
        val comboRod = combo >= 2 && distance in 2.5..8.0
        val randomCloseRod = distance in 2.0..4.0 && RandomUtils.randomIntInRange(1, 100) <= 30
        
        return inMainRange || comboRod || randomCloseRod
    }

    // Block breaking logic
    private fun tryBreakBlocksInPath(distance: Double) {
        if (mc.thePlayer == null || mc.theWorld == null || opponent() == null) return
        if (System.currentTimeMillis() - lastBlockBreakTime < blockBreakCooldown) return
        if (distance < 5) return // Don't break when close
        
        val playerPos = mc.thePlayer.position
        val opponentPos = opponent()!!.position
        
        // Check blocks in front of player
        val frontBlock = WorldUtils.blockInFront(mc.thePlayer, 2f, 0.5f)
        if (frontBlock != Blocks.air && frontBlock != null) {
            // Get the block position in front
            val lookVec = mc.thePlayer.lookVec
            val checkPos = BlockPos(
                playerPos.x + lookVec.xCoord * 2,
                playerPos.y.toDouble(),
                playerPos.z + lookVec.zCoord * 2
            )
            
            val block = mc.theWorld.getBlockState(checkPos).block
            
            // Only break breakable blocks (not bedrock, barriers, etc)
            if (block != Blocks.air && block != Blocks.bedrock && block != Blocks.barrier) {
                // Switch to appropriate tool if available (pickaxe/axe)
                val currentItem = mc.thePlayer.heldItem?.unlocalizedName?.lowercase() ?: ""
                if (!currentItem.contains("pickaxe") && !currentItem.contains("axe")) {
                    // Try to switch to tool
                    Inventory.setInvItem("pickaxe") || Inventory.setInvItem("axe")
                }
                
                // Start breaking
                mc.playerController.clickBlock(checkPos, EnumFacing.UP)
                Mouse.startLeftAC()
                
                TimeUtils.setTimeout({
                    Mouse.stopLeftAC()
                    Inventory.setInvItem("sword") // Switch back to sword
                }, RandomUtils.randomIntInRange(100, 300))
                
                lastBlockBreakTime = System.currentTimeMillis()
            }
        }
    }

    // Block placing logic for defense
    private fun tryPlaceDefensiveBlock(distance: Double) {
        if (mc.thePlayer == null || mc.theWorld == null || opponent() == null) return
        if (System.currentTimeMillis() - lastBlockPlaceTime < blockPlaceCooldown) return
        if (distance < 10) return // Only place at range
        
        // Check if opponent has bow
        val oppItem = opponent()!!.heldItem?.unlocalizedName?.lowercase() ?: ""
        if (!oppItem.contains("bow")) return
        
        // Check if we have blocks
        var hasBlocks = false
        for (i in 0..8) {
            val stack = mc.thePlayer.inventory.getStackInSlot(i)
            if (stack != null) {
                val itemName = stack.unlocalizedName.lowercase()
                if (itemName.contains("stone") || itemName.contains("dirt") || 
                    itemName.contains("wood") || itemName.contains("plank")) {
                    hasBlocks = true
                    break
                }
            }
        }
        
        if (!hasBlocks) return
        
        // Switch to blocks
        Inventory.setInvItem("stone") || Inventory.setInvItem("wood") || 
        Inventory.setInvItem("plank") || Inventory.setInvItem("dirt")
        
        // Place block in front of player as defense
        val playerPos = mc.thePlayer.position
        val placePos = BlockPos(playerPos.x, playerPos.y, playerPos.z).offset(
            mc.thePlayer.horizontalFacing
        )
        
        if (mc.theWorld.getBlockState(placePos).block == Blocks.air) {
            Mouse.rClick(RandomUtils.randomIntInRange(50, 100))
            
            TimeUtils.setTimeout({
                Inventory.setInvItem("sword") // Switch back
            }, 100)
            
            lastBlockPlaceTime = System.currentTimeMillis()
        }
    }

    override fun onTick() {
        var needJump = false
        if (mc.thePlayer != null) {
            if (WorldUtils.blockInFront(mc.thePlayer, 2f, 0.5f) != Blocks.air && mc.thePlayer.onGround) {
                needJump = true
                Movement.singleJump(RandomUtils.randomIntInRange(150, 250))
            }
        }
        if (opponent() != null && mc.theWorld != null && mc.thePlayer != null) {
            if (!mc.thePlayer.isSprinting) {
                Movement.startSprinting()
            }

            val distance = EntityUtils.getDistanceNoY(mc.thePlayer, opponent())

            if (distance < (DuckDueller.config?.maxDistanceLook ?: 150)) {
                Mouse.startTracking()
            } else {
                Mouse.stopTracking()
            }

            if (distance < (DuckDueller.config?.maxDistanceAttack ?: 10)) {
                if (mc.thePlayer.heldItem != null && mc.thePlayer.heldItem.unlocalizedName.lowercase().contains("sword")) {
                    Mouse.startLeftAC()
                }
            } else {
                Mouse.stopLeftAC()
            }

            // Block placing priority (defense against bow)
            tryPlaceDefensiveBlock(distance)

            // Block breaking (offensive)
            tryBreakBlocksInPath(distance)

            if (distance > 8.8) {
                if (opponent() != null && opponent()!!.heldItem != null && opponent()!!.heldItem.unlocalizedName.lowercase().contains("bow")) {
                    if (WorldUtils.blockInFront(mc.thePlayer, 2f, 0.5f) == Blocks.air) {
                        if (!EntityUtils.entityFacingAway(mc.thePlayer, opponent()!!) && !needJump) {
                            Movement.stopJumping()
                        } else {
                            Movement.startJumping()
                        }
                    } else {
                        Movement.startJumping()
                    }
                } else {
                    Movement.startJumping()
                }
            } else {
                if (!needJump) {
                    Movement.stopJumping()
                }
            }

            val movePriority = arrayListOf(0, 0)
            var clear = false
            var randomStrafe = false

            if (distance < 1 || (distance < 2.7 && combo >= 1)) {
                Movement.stopForward()
            } else {
                if (!tapping) {
                    Movement.startForward()
                }
            }

            if (distance < 1.5 && mc.thePlayer.heldItem != null && !mc.thePlayer.heldItem.unlocalizedName.lowercase().contains("sword")) {
                Inventory.setInvItem("sword")
                Mouse.rClickUp()
                Mouse.startLeftAC()
            }

            // ENHANCED ROD SPAM - Expanded ranges and more aggressive
            if (shouldUseRod(distance) && !EntityUtils.entityFacingAway(mc.thePlayer, opponent()!!)) {
                useRod()
                lastRodTime = System.currentTimeMillis()
            }

            if (combo >= 3 && distance >= 3.2 && mc.thePlayer.onGround) {
                Movement.singleJump(RandomUtils.randomIntInRange(100, 150))
            }

            if ((EntityUtils.entityFacingAway(mc.thePlayer, opponent()!!) && distance in 3.5f..30f) || (distance in 28.0..33.0 && !EntityUtils.entityFacingAway(mc.thePlayer, opponent()!!))) {
                if (distance > 5 && !Mouse.isUsingProjectile() && shotsFired < maxArrows) {
                    clear = true
                    useBow(distance, fun () {
                        shotsFired++
                    })
                } else {
                    clear = false
                    if (WorldUtils.leftOrRightToPoint(mc.thePlayer, Vec3(0.0, 0.0, 0.0))) {
                        movePriority[0] += 4
                    } else {
                        movePriority[1] += 4
                    }
                }
            } else {
                if (EntityUtils.entityFacingAway(mc.thePlayer, opponent()!!)) {
                    if (WorldUtils.leftOrRightToPoint(mc.thePlayer, Vec3(0.0, 0.0, 0.0))) {
                        movePriority[0] += 4
                    } else {
                        movePriority[1] += 4
                    }
                } else {
                    if (distance in 15f..8f) {
                        randomStrafe = true
                    } else {
                        randomStrafe = false
                        if (opponent() != null && opponent()!!.heldItem != null && (opponent()!!.heldItem.unlocalizedName.lowercase().contains("bow") || opponent()!!.heldItem.unlocalizedName.lowercase().contains("rod"))) {
                            randomStrafe = true
                            if (distance < 15 && !needJump) {
                                Movement.stopJumping()
                            }
                        } else {
                            if (distance < 8) {
                                val rotations = EntityUtils.getRotations(opponent()!!, mc.thePlayer, false)
                                if (rotations != null) {
                                    if (rotations[0] < 0) {
                                        movePriority[1] += 5
                                    } else {
                                        movePriority[0] += 5
                                    }
                                }
                            }
                        }
                    }
                }
            }

            handle(clear, randomStrafe, movePriority)
        }
    }

}
