/*
 * Copyright (c) 2014 CIYAM Developers

 Distributed under the MIT/X11 software license, please refer to the file license.txt
 in the root project directory or http://www.opensource.org/licenses/mit-license.php.
*/

package brs.at

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.slf4j.helpers.NOPLogger

internal class AtMachineProcessor(private val machineData: AtMachineState, enableLogger: Boolean) {

    private val logger: Logger
    private val `fun` = Fun()

    private val addrs: Int
        get() {
            if (machineData.machineState!!.pc + 4 + 4 >= machineData.getcSize()) {
                return -1
            }

            `fun`.addr1 = machineData.apCode.getInt(machineData.machineState!!.pc + 1)
            `fun`.addr2 = machineData.apCode.getInt(machineData.machineState!!.pc + 1 + 4)
            return if (!validAddr(`fun`.addr1, false) || !validAddr(`fun`.addr2, false)) {
                -1
            } else 0

        }

    private val addrOff: Int
        get() {
            if (machineData.machineState!!.pc + 5 >= machineData.getcSize()) {
                return -1
            }

            `fun`.addr1 = machineData.apCode.getInt(machineData.machineState!!.pc + 1)
            `fun`.off = machineData.apCode.get(machineData.machineState!!.pc + 5)
            return if (!validAddr(`fun`.addr1, false) || !validAddr(machineData.machineState!!.pc + `fun`.off, true)) {
                -1
            } else 0

        }

    private val addrsOff: Int
        get() {
            if (machineData.machineState!!.pc + 9 >= machineData.getcSize()) {
                return -1
            }

            `fun`.addr1 = machineData.apCode.getInt(machineData.machineState!!.pc + 1)
            `fun`.addr2 = machineData.apCode.getInt(machineData.machineState!!.pc + 5)
            `fun`.off = machineData.apCode.get(machineData.machineState!!.pc + 9)

            return if (!validAddr(`fun`.addr1, false) ||
                    !validAddr(`fun`.addr2, false) ||
                    !validAddr(machineData.machineState!!.pc + `fun`.off, true)) {
                -1
            } else 0

        }

    private val funAddr: Int
        get() {
            if (machineData.machineState!!.pc + 4 + 4 >= machineData.getcSize()) {
                return -1
            }

            `fun`.`fun` = machineData.apCode.getShort(machineData.machineState!!.pc + 1)
            `fun`.addr1 = machineData.apCode.getInt(machineData.machineState!!.pc + 1 + 2)
            return if (!validAddr(`fun`.addr1, false)) {
                -1
            } else 0

        }

    private val funAddrs: Int
        get() {
            if (machineData.machineState!!.pc + 4 + 4 + 2 >= machineData.getcSize()) {
                return -1
            }

            `fun`.`fun` = machineData.apCode.getShort(machineData.machineState!!.pc + 1)
            `fun`.addr3 = machineData.apCode.getInt(machineData.machineState!!.pc + 1 + 2)
            `fun`.addr2 = machineData.apCode.getInt(machineData.machineState!!.pc + 1 + 2 + 4)

            return if (!validAddr(`fun`.addr3, false) || !validAddr(`fun`.addr2, false)) {
                -1
            } else 0

        }

    private val addressVal: Int
        get() {
            if (machineData.machineState!!.pc + 4 + 8 >= machineData.getcSize()) {
                return -1
            }

            `fun`.addr1 = machineData.apCode.getInt(machineData.machineState!!.pc + 1)
            `fun`.`val` = machineData.apCode.getLong(machineData.machineState!!.pc + 1 + 4)

            return if (!validAddr(`fun`.addr1, false)) {
                -1
            } else 0

        }

    init {
        this.logger = if (enableLogger) LoggerFactory.getLogger(AtMachineProcessor::class.java) else NOPLogger.NOP_LOGGER
    }

    private fun getFun(): Int {

        if (machineData.machineState!!.pc + 2 >= machineData.getcSize())
            return -1
        else {
            `fun`.`fun` = machineData.apCode.getShort(machineData.machineState!!.pc + 1)
        }

        return 0
    }

    private fun getAddr(isCode: Boolean): Int {
        if (machineData.machineState!!.pc + 4 >= machineData.getcSize()) {
            return -1
        }

        `fun`.addr1 = machineData.apCode.getInt(machineData.apCode.position() + machineData.machineState!!.pc + 1)
        return if (!validAddr(`fun`.addr1, isCode)) {
            -1
        } else 0

    }

    private fun validAddr(addr: Int, isCode: Boolean): Boolean {
        if (addr < 0) {
            return false
        }

        return if (!isCode && (addr.toLong() * 8 + 8 > Integer.MAX_VALUE.toLong() || addr * 8 + 8 > machineData.getdSize())) {
            false
        } else !isCode || addr < machineData.getcSize()

    }

    fun processOp(disassemble: Boolean, determineJumps: Boolean): Int {
        var rc = 0

        if (machineData.getcSize() < 1 || machineData.machineState!!.pc >= machineData.getcSize())
            return 0

        if (determineJumps) {
            machineData.machineState!!.jumps.add(machineData.machineState!!.pc)
        }

        val op = machineData.apCode.get(machineData.machineState!!.pc)
        if (op > 0 && disassemble && !determineJumps && logger.isDebugEnabled) {
            logger.debug(String.format("%8x", machineData.machineState!!.pc).replace(' ', '0'))
            if (machineData.machineState!!.pc == machineData.machineState!!.opc)
                logger.debug("* ")
            else
                logger.debug("  ")
        }

        if (op == OpCode.E_OP_CODE_NOP) {
            if (disassemble) {
                if (!determineJumps && logger.isDebugEnabled)
                    logger.debug("NOP")
                ++rc
            } else {
                ++rc
                ++machineData.machineState!!.pc
            }
        } else if (op == OpCode.E_OP_CODE_SET_VAL) {
            rc = addressVal

            if (rc == 0 || disassemble) {
                rc = 13
                if (disassemble) {
                    if (!determineJumps && logger.isDebugEnabled)
                        logger.debug("SET @ {} {}", String.format("%8s", `fun`.addr1).replace(' ', '0'), String.format("#%16s", java.lang.Long.toHexString(`fun`.`val`)).replace(' ', '0'))
                } else {
                    machineData.machineState!!.pc += rc
                    machineData.apData!!.putLong(`fun`.addr1 * 8, `fun`.`val`)
                    machineData.apData!!.clear()

                }
            }
        } else if (op == OpCode.E_OP_CODE_SET_DAT) {
            rc = addrs

            if (rc == 0 || disassemble) {
                rc = 9
                if (disassemble) {
                    if (!determineJumps && logger.isDebugEnabled)
                        logger.debug("SET @ {} \${}", String.format("%8s", `fun`.addr1).replace(' ', '0'), String.format("%8s", `fun`.addr2).replace(' ', '0'))
                } else {
                    machineData.machineState!!.pc += rc
                    machineData.apData!!.putLong(`fun`.addr1 * 8, machineData.apData!!.getLong(`fun`.addr2 * 8))
                    machineData.apData!!.clear()

                }
            }
        } else if (op == OpCode.E_OP_CODE_CLR_DAT) {
            rc = getAddr(false)

            if (rc == 0 || disassemble) {
                rc = 5
                if (disassemble) {
                    if (!determineJumps && logger.isDebugEnabled)
                        logger.debug("CLR @ {}", String.format("%8s", `fun`.addr1))
                } else {
                    machineData.machineState!!.pc += rc
                    machineData.apData!!.putLong(`fun`.addr1 * 8, 0.toLong())
                    machineData.apData!!.clear()
                }
            }
        } else if (op == OpCode.E_OP_CODE_INC_DAT ||
                op == OpCode.E_OP_CODE_DEC_DAT ||
                op == OpCode.E_OP_CODE_NOT_DAT) {
            rc = getAddr(false)
            if (rc == 0 || disassemble) {
                rc = 5
                if (disassemble) {
                    if (!determineJumps) {
                        if (op == OpCode.E_OP_CODE_INC_DAT) {
                            logger.debug("INC @")
                        } else if (op == OpCode.E_OP_CODE_DEC_DAT) {
                            logger.debug("DEC @")
                        } else if (op == OpCode.E_OP_CODE_NOT_DAT) {
                            logger.debug("NOT @")
                        }
                        if (logger.isDebugEnabled) {
                            logger.debug(String.format("%d", `fun`.addr1).replace(' ', '0'))
                        }
                    }
                } else {
                    machineData.machineState!!.pc += rc
                    if (op == OpCode.E_OP_CODE_INC_DAT) {
                        val incData = machineData.apData!!.getLong(`fun`.addr1 * 8) + 1
                        machineData.apData!!.putLong(`fun`.addr1 * 8, incData)
                        machineData.apData!!.clear()
                    } else if (op == OpCode.E_OP_CODE_DEC_DAT) {
                        val incData = machineData.apData!!.getLong(`fun`.addr1 * 8) - 1
                        machineData.apData!!.putLong(`fun`.addr1 * 8, incData)
                        machineData.apData!!.clear()
                    } else if (op == OpCode.E_OP_CODE_NOT_DAT) {
                        val incData = machineData.apData!!.getLong(`fun`.addr1 * 8)
                        machineData.apData!!.putLong(`fun`.addr1 * 8, incData.inv())
                        machineData.apData!!.clear()
                    }
                }
            }
        } else if (op == OpCode.E_OP_CODE_ADD_DAT ||
                op == OpCode.E_OP_CODE_SUB_DAT ||
                op == OpCode.E_OP_CODE_MUL_DAT ||
                op == OpCode.E_OP_CODE_DIV_DAT) {
            rc = addrs

            if (rc == 0 || disassemble) {
                rc = 9
                if (disassemble) {
                    if (!determineJumps) {
                        if (op == OpCode.E_OP_CODE_ADD_DAT) {
                            logger.debug("ADD @")
                        } else if (op == OpCode.E_OP_CODE_SUB_DAT) {
                            logger.debug("SUB @")
                        } else if (op == OpCode.E_OP_CODE_MUL_DAT) {
                            logger.debug("MUL @")
                        } else if (op == OpCode.E_OP_CODE_DIV_DAT) {
                            logger.debug("DIV @")
                        }
                        if (logger.isDebugEnabled) {
                            logger.debug("{} \${}", String.format("%8x", `fun`.addr1).replace(' ', '0'), String.format("%8s", `fun`.addr2).replace(' ', '0'))
                        }
                    }
                } else {
                    val `val` = machineData.apData!!.getLong(`fun`.addr2 * 8)
                    if (op == OpCode.E_OP_CODE_DIV_DAT && `val` == 0L)
                        rc = -2
                    else {
                        machineData.machineState!!.pc += rc
                        if (op == OpCode.E_OP_CODE_ADD_DAT) {
                            val addData1 = machineData.apData!!.getLong(`fun`.addr1 * 8)
                            val addData2 = machineData.apData!!.getLong(`fun`.addr2 * 8)
                            machineData.apData!!.putLong(`fun`.addr1 * 8, addData1 + addData2)
                            machineData.apData!!.clear()
                        } else if (op == OpCode.E_OP_CODE_SUB_DAT) {
                            val addData1 = machineData.apData!!.getLong(`fun`.addr1 * 8)
                            val addData2 = machineData.apData!!.getLong(`fun`.addr2 * 8)
                            machineData.apData!!.putLong(`fun`.addr1 * 8, addData1 - addData2)
                            machineData.apData!!.clear()
                        } else if (op == OpCode.E_OP_CODE_MUL_DAT) {
                            val addData1 = machineData.apData!!.getLong(`fun`.addr1 * 8)
                            val addData2 = machineData.apData!!.getLong(`fun`.addr2 * 8)
                            machineData.apData!!.putLong(`fun`.addr1 * 8, addData1 * addData2)
                            machineData.apData!!.clear()
                        } else if (op == OpCode.E_OP_CODE_DIV_DAT) {

                            val addData1 = machineData.apData!!.getLong(`fun`.addr1 * 8)
                            val addData2 = machineData.apData!!.getLong(`fun`.addr2 * 8)
                            machineData.apData!!.putLong(`fun`.addr1 * 8, addData1 / addData2)
                            machineData.apData!!.clear()
                        }
                    }
                }
            }
        } else if (op == OpCode.E_OP_CODE_BOR_DAT ||
                op == OpCode.E_OP_CODE_AND_DAT ||
                op == OpCode.E_OP_CODE_XOR_DAT) {
            rc = addrs

            if (rc == 0 || disassemble) {
                rc = 9
                if (disassemble) {
                    if (!determineJumps && logger.isDebugEnabled) {
                        if (op == OpCode.E_OP_CODE_BOR_DAT) {
                            logger.debug("BOR @")
                        } else if (op == OpCode.E_OP_CODE_AND_DAT) {
                            logger.debug("AND @")
                        } else if (op == OpCode.E_OP_CODE_XOR_DAT) {
                            logger.debug("XOR @")
                        }
                        logger.debug(String.format("%16s $%16s", `fun`.addr1, `fun`.addr2).replace(' ', '0'))
                    }
                } else {
                    machineData.machineState!!.pc += rc
                    val `val` = machineData.apData!!.getLong(`fun`.addr2 * 8)

                    if (op == OpCode.E_OP_CODE_BOR_DAT) {
                        val incData = machineData.apData!!.getLong(`fun`.addr1 * 8)
                        machineData.apData!!.putLong(`fun`.addr1 * 8, incData or `val`)
                        machineData.apData!!.clear()
                    } else if (op == OpCode.E_OP_CODE_AND_DAT) {
                        val incData = machineData.apData!!.getLong(`fun`.addr1 * 8)
                        machineData.apData!!.putLong(`fun`.addr1 * 8, incData and `val`)
                        machineData.apData!!.clear()
                    } else if (op == OpCode.E_OP_CODE_XOR_DAT) {
                        val incData = machineData.apData!!.getLong(`fun`.addr1 * 8)
                        machineData.apData!!.putLong(`fun`.addr1 * 8, incData xor `val`)
                        machineData.apData!!.clear()
                    }
                }
            }
        } else if (op == OpCode.E_OP_CODE_SET_IND) {
            rc = addrs

            if (rc == 0) {
                rc = 9
                if (disassemble) {
                    if (!determineJumps && logger.isDebugEnabled)
                        logger.debug("SET @ {} {}", String.format("%8s", `fun`.addr1).replace(' ', '0'), String.format("$($%8s", `fun`.addr2).replace(' ', '0'))
                } else {
                    val addr = machineData.apData!!.getLong(`fun`.addr2 * 8)

                    if (!validAddr(addr.toInt(), false))
                        rc = -1
                    else {
                        machineData.machineState!!.pc += rc
                        val `val` = machineData.apData!!.getLong(addr.toInt() * 8)
                        machineData.apData!!.putLong(`fun`.addr1 * 8, `val`)
                        machineData.apData!!.clear()
                    }
                }
            }
        } else if (op == OpCode.E_OP_CODE_SET_IDX) {
            val addr1 = `fun`.addr1
            val addr2 = `fun`.addr2
            val size = 8

            rc = addrs

            if (rc == 0 || disassemble) {
                machineData.apCode.position(size)
                rc = getAddr(false)
                machineData.apCode.position(machineData.apCode.position() - size)

                if (rc == 0 || disassemble) {
                    rc = 13
                    val base = machineData.apData!!.getLong(addr2 * 8)
                    val offs = machineData.apData!!.getLong(`fun`.addr1 * 8)

                    val addr = base + offs

                    logger.debug("addr1: {}", `fun`.addr1)
                    if (!validAddr(addr.toInt(), false)) {
                        rc = -1
                    } else {
                        machineData.machineState!!.pc += rc
                        machineData.apData!!.putLong(addr1 * 8, machineData.apData!!.getLong(addr.toInt() * 8))
                        machineData.apData!!.clear()
                    }
                }
            }
        } else if (op == OpCode.E_OP_CODE_PSH_DAT || op == OpCode.E_OP_CODE_POP_DAT) {
            rc = getAddr(false)
            if (rc == 0 || disassemble) {
                rc = 5
                if (disassemble) {
                    if (!determineJumps) {
                        if (op == OpCode.E_OP_CODE_PSH_DAT)
                            logger.debug("PSH $")
                        else
                            logger.debug("POP @")
                        if (logger.isDebugEnabled) {
                            logger.debug(String.format("%8s", `fun`.addr1).replace(' ', '0'))
                        }
                    }
                } else if (op == OpCode.E_OP_CODE_PSH_DAT && machineData.machineState!!.us == machineData.getcUserStackBytes() / 8 || op == OpCode.E_OP_CODE_POP_DAT && machineData.machineState!!.us == 0) {
                    rc = -1
                } else {
                    machineData.machineState!!.pc += rc
                    if (op == OpCode.E_OP_CODE_PSH_DAT) {
                        val `val` = machineData.apData!!.getLong(`fun`.addr1 * 8)
                        machineData.machineState!!.us++
                        machineData.apData!!.putLong(machineData.getdSize() +
                                machineData.getcCallStackBytes() +
                                machineData.getcUserStackBytes() - machineData.machineState!!.us * 8, `val`)
                        machineData.apData!!.clear()
                    } else {
                        val `val` = machineData.apData!!.getLong(machineData.getdSize() +
                                machineData.getcCallStackBytes() +
                                machineData.getcUserStackBytes() - machineData.machineState!!.us * 8)
                        machineData.machineState!!.us--
                        machineData.apData!!.putLong(`fun`.addr1 * 8, `val`)
                        machineData.apData!!.clear()
                    }
                }
            }
        } else if (op == OpCode.E_OP_CODE_JMP_SUB) {
            rc = getAddr(true)

            if (rc == 0 || disassemble) {
                rc = 5
                if (disassemble) {
                    if (!determineJumps && logger.isDebugEnabled)
                        logger.debug("JSR : {}", String.format("%8s", `fun`.addr1).replace(' ', '0'))
                } else {
                    if (machineData.machineState!!.cs == machineData.getcCallStackBytes() / 8)
                        rc = -1
                    else if (machineData.machineState!!.jumps.contains(`fun`.addr1)) {
                        machineData.machineState!!.cs++
                        machineData.apData!!.putLong(machineData.getdSize() + machineData.getcCallStackBytes() - machineData.machineState!!.cs * 8,
                                (machineData.machineState!!.pc + rc).toLong())
                        machineData.apData!!.clear()
                        machineData.machineState!!.pc = `fun`.addr1
                    } else
                        rc = -2
                }
            }
        } else if (op == OpCode.E_OP_CODE_RET_SUB) {
            rc = 1

            if (disassemble) {
                if (!determineJumps)
                    logger.debug("RET\n")
            } else {
                if (machineData.machineState!!.cs == 0)
                    rc = -1
                else {
                    val `val` = machineData.apData!!.getLong(machineData.getdSize() + machineData.getcCallStackBytes() - machineData.machineState!!.cs * 8)
                    machineData.machineState!!.cs--
                    val addr = `val`.toInt()
                    if (machineData.machineState!!.jumps.contains(addr))
                        machineData.machineState!!.pc = addr
                    else
                        rc = -2
                }
            }
        } else if (op == OpCode.E_OP_CODE_IND_DAT) {
            rc = addrs

            if (rc == 0) {
                rc = 9
                if (disassemble) {
                    if (!determineJumps && logger.isDebugEnabled)
                        logger.debug("SET @{} {}", String.format("($%8s)", `fun`.addr1).replace(' ', '0'), String.format("$%8s", `fun`.addr2).replace(' ', '0'))
                } else {
                    val addr = machineData.apData!!.getLong(`fun`.addr1 * 8)

                    if (!validAddr(addr.toInt(), false))
                        rc = -1
                    else {
                        machineData.machineState!!.pc += rc
                        machineData.apData!!.putLong(addr.toInt() * 8, machineData.apData!!.getLong(`fun`.addr2 * 8))
                        machineData.apData!!.clear()
                    }
                }
            }
        } else if (op == OpCode.E_OP_CODE_IDX_DAT) {
            val addr1 = `fun`.addr1
            val addr2 = `fun`.addr2
            val size = 8

            rc = addrs

            if (rc == 0 || disassemble) {
                machineData.apCode.position(size)
                rc = getAddr(false)
                machineData.apCode.position(machineData.apCode.position() - size)

                if (rc == 0 || disassemble) {
                    rc = 13
                    if (disassemble) {
                        if (!determineJumps && logger.isDebugEnabled)
                            logger.debug("SET @{} {}", String.format("($%8s+$%8s)", addr1, addr2).replace(' ', '0'), String.format("$%8s", `fun`.addr1).replace(' ', '0'))
                    } else {
                        val addr = machineData.apData!!.getLong(addr1 * 8) + machineData.apData!!.getLong(addr2 * 8)

                        if (!validAddr(addr.toInt(), false))
                            rc = -1
                        else {
                            machineData.machineState!!.pc += rc
                            machineData.apData!!.putLong(addr.toInt() * 8, machineData.apData!!.getLong(`fun`.addr1 * 8))
                            machineData.apData!!.clear()
                        }
                    }
                }
            }
        } else if (op == OpCode.E_OP_CODE_MOD_DAT) {
            rc = addrs

            if (rc == 0 || disassemble) {
                rc = 9
                if (disassemble) {
                    if (!determineJumps && logger.isDebugEnabled)
                        logger.debug("MOD @{} \${}", String.format("%8x", `fun`.addr1).replace(' ', '0'), String.format("%8s", `fun`.addr2).replace(' ', '0'))
                } else {
                    val modData1 = machineData.apData!!.getLong(`fun`.addr1 * 8)
                    val modData2 = machineData.apData!!.getLong(`fun`.addr2 * 8)

                    if (modData2 == 0L)
                        rc = -2
                    else {
                        machineData.machineState!!.pc += rc
                        machineData.apData!!.putLong(`fun`.addr1 * 8, modData1 % modData2)
                    }
                }
            }
        } else if (op == OpCode.E_OP_CODE_SHL_DAT || op == OpCode.E_OP_CODE_SHR_DAT) {
            rc = addrs

            if (rc == 0 || disassemble) {
                rc = 9
                if (disassemble) {
                    if (!determineJumps && logger.isDebugEnabled) {
                        if (op == OpCode.E_OP_CODE_SHL_DAT)
                            logger.debug("SHL @{} \${}", String.format("%8x", `fun`.addr1).replace(' ', '0'), String.format("%8x", `fun`.addr2).replace(' ', '0'))
                        else
                            logger.debug("SHR @{} \${}", String.format("%8x", `fun`.addr1).replace(' ', '0'), String.format("%8x", `fun`.addr2).replace(' ', '0'))
                    }
                } else {
                    machineData.machineState!!.pc += rc
                    val `val` = machineData.apData!!.getLong(`fun`.addr1 * 8)
                    var shift = machineData.apData!!.getLong(`fun`.addr2 * 8)
                    if (shift < 0)
                        shift = 0
                    else if (shift > 63)
                        shift = 63

                    if (op == OpCode.E_OP_CODE_SHL_DAT)
                        machineData.apData!!.putLong(`fun`.addr1 * 8, `val` shl shift)
                    else
                        machineData.apData!!.putLong(`fun`.addr1 * 8, `val`.ushr(shift))
                }
            }
        } else if (op == OpCode.E_OP_CODE_JMP_ADR) {
            rc = getAddr(true)

            if (rc == 0 || disassemble) {
                rc = 5
                if (disassemble) {
                    if (!determineJumps && logger.isDebugEnabled)
                        logger.debug("JMP : {}", String.format("%8x", `fun`.addr1))
                } else if (machineData.machineState!!.jumps.contains(`fun`.addr1))
                    machineData.machineState!!.pc = `fun`.addr1
                else
                    rc = -2
            }
        } else if (op == OpCode.E_OP_CODE_BZR_DAT || op == OpCode.E_OP_CODE_BNZ_DAT) {
            rc = addrOff

            if (rc == 0 || disassemble) {
                rc = 6
                if (disassemble) {
                    if (!determineJumps) {
                        if (op == OpCode.E_OP_CODE_BZR_DAT)
                            logger.debug("BZR $")
                        else
                            logger.debug("BNZ $")

                        if (logger.isDebugEnabled) {
                            logger.debug("{}, :{}", String.format("%8x", `fun`.addr1).replace(' ', '0'), String.format("%8x", machineData.machineState!!.pc + `fun`.off).replace(' ', '0'))
                        }
                    }
                } else {
                    val `val` = machineData.apData!!.getLong(`fun`.addr1 * 8)
                    if (op == OpCode.E_OP_CODE_BZR_DAT && `val` == 0L || op == OpCode.E_OP_CODE_BNZ_DAT && `val` != 0L) {
                        if (machineData.machineState!!.jumps.contains(machineData.machineState!!.pc + `fun`.off))
                            machineData.machineState!!.pc += `fun`.off.toInt()
                        else
                            rc = -2
                    } else
                        machineData.machineState!!.pc += rc
                }
            }
        } else if (op == OpCode.E_OP_CODE_BGT_DAT || op == OpCode.E_OP_CODE_BLT_DAT ||
                op == OpCode.E_OP_CODE_BGE_DAT || op == OpCode.E_OP_CODE_BLE_DAT ||
                op == OpCode.E_OP_CODE_BEQ_DAT || op == OpCode.E_OP_CODE_BNE_DAT) {
            rc = addrsOff

            if (rc == 0 || disassemble) {
                rc = 10
                if (disassemble) {
                    if (!determineJumps) {
                        if (op == OpCode.E_OP_CODE_BGT_DAT)
                            logger.debug("BGT $")
                        else if (op == OpCode.E_OP_CODE_BLT_DAT)
                            logger.debug("BLT $")
                        else if (op == OpCode.E_OP_CODE_BGE_DAT)
                            logger.debug("BGE $")
                        else if (op == OpCode.E_OP_CODE_BLE_DAT)
                            logger.debug("BLE $")
                        else if (op == OpCode.E_OP_CODE_BEQ_DAT)
                            logger.debug("BEQ $")
                        else
                            logger.debug("BNE $")

                        if (logger.isDebugEnabled) {
                            logger.debug("{} \${} :{}", String.format("%8x", `fun`.addr1).replace(' ', '0'), String.format("%8x", `fun`.addr2).replace(' ', '0'), String.format("%8x", machineData.machineState!!.pc + `fun`.off).replace(' ', '0'))
                        }
                    }
                } else {
                    val val1 = machineData.apData!!.getLong(`fun`.addr1 * 8)
                    val val2 = machineData.apData!!.getLong(`fun`.addr2 * 8)

                    if (op == OpCode.E_OP_CODE_BGT_DAT && val1 > val2 ||
                            op == OpCode.E_OP_CODE_BLT_DAT && val1 < val2 ||
                            op == OpCode.E_OP_CODE_BGE_DAT && val1 >= val2 ||
                            op == OpCode.E_OP_CODE_BLE_DAT && val1 <= val2 ||
                            op == OpCode.E_OP_CODE_BEQ_DAT && val1 == val2 ||
                            op == OpCode.E_OP_CODE_BNE_DAT && val1 != val2) {

                        if (machineData.machineState!!.jumps.contains(machineData.machineState!!.pc + `fun`.off))
                            machineData.machineState!!.pc += `fun`.off.toInt()
                        else
                            rc = -2
                    } else
                        machineData.machineState!!.pc += rc
                }
            }
        } else if (op == OpCode.E_OP_CODE_SLP_DAT) {
            rc = getAddr(true)

            if (rc == 0 || disassemble) {
                rc = 1 + 4

                if (disassemble) {
                    if (!determineJumps && logger.isDebugEnabled)
                        logger.debug("SLP @ {}", String.format("%8x", `fun`.addr1))

                } else {
                    machineData.machineState!!.pc += rc
                    var numBlocks = machineData.apData!!.getLong(`fun`.addr1 * 8).toInt()
                    if (numBlocks < 0)
                        numBlocks = 0
                    val maxNumBlocks = AtConstants.getMaxWaitForNumOfBlocks(machineData.creationBlockHeight).toInt()
                    if (numBlocks > maxNumBlocks)
                        numBlocks = maxNumBlocks
                    machineData.waitForNumberOfBlocks = numBlocks
                    machineData.machineState!!.stopped = true
                }
            }
        } else if (op == OpCode.E_OP_CODE_FIZ_DAT || op == OpCode.E_OP_CODE_STZ_DAT) {
            rc = getAddr(false)

            if (rc == 0 || disassemble) {
                rc = 5
                if (disassemble) {
                    if (!determineJumps) {
                        if (op == OpCode.E_OP_CODE_FIZ_DAT)
                            logger.debug("FIZ @")
                        else
                            logger.debug("STZ @")

                        if (logger.isDebugEnabled) {
                            logger.debug(String.format("%8x", `fun`.addr1).replace(' ', '0'))
                        }
                    }
                } else {
                    if (machineData.apData!!.getLong(`fun`.addr1 * 8) == 0L) {
                        if (op == OpCode.E_OP_CODE_STZ_DAT) {
                            machineData.machineState!!.pc += rc
                            machineData.machineState!!.stopped = true
                            machineData.setFreeze(true)
                        } else {
                            machineData.machineState!!.pc = machineData.machineState!!.pcs
                            machineData.machineState!!.finished = true
                            machineData.setFreeze(true)
                        }
                    } else {
                        rc = 5
                        machineData.machineState!!.pc += rc
                    }
                }
            }
        } else if (op == OpCode.E_OP_CODE_FIN_IMD || op == OpCode.E_OP_CODE_STP_IMD) {
            rc = 1

            if (disassemble) {
                if (!determineJumps) {
                    if (op == OpCode.E_OP_CODE_FIN_IMD)
                        logger.debug("FIN\n")
                    else
                        logger.debug("STP")
                }
            } else if (op == OpCode.E_OP_CODE_STP_IMD) {
                machineData.machineState!!.pc += rc
                machineData.machineState!!.stopped = true
                machineData.setFreeze(true)
            } else {
                machineData.machineState!!.pc = machineData.machineState!!.pcs
                machineData.machineState!!.finished = true
                machineData.setFreeze(true)
            }
        } else if (op == OpCode.E_OP_CODE_SLP_IMD) {
            rc = 1

            if (disassemble) {
                if (!determineJumps && logger.isDebugEnabled) {
                    logger.debug("SLP\n")
                }
            } else {
                machineData.machineState!!.pc += rc
                machineData.machineState!!.stopped = true
                machineData.setFreeze(true)
            }

        } else if (op == OpCode.E_OP_CODE_SET_PCS) {
            rc = 1

            if (disassemble) {
                if (!determineJumps && logger.isDebugEnabled)
                    logger.debug("PCS")
            } else {
                machineData.machineState!!.pc += rc
                machineData.machineState!!.pcs = machineData.machineState!!.pc
            }
        } else if (op == OpCode.E_OP_CODE_EXT_FUN) {
            rc = getFun()

            if (rc == 0 || disassemble) {
                rc = 1 + 2

                if (disassemble) {
                    if (!determineJumps && logger.isDebugEnabled)
                        logger.debug("FUN {}", `fun`.`fun`)
                } else {
                    machineData.machineState!!.pc += rc
                    AtApiController.func(`fun`.`fun`.toInt(), machineData)
                }
            }
        } else if (op == OpCode.E_OP_CODE_EXT_FUN_DAT) {
            rc = funAddr
            if (rc == 0) {
                rc = 7

                if (disassemble) {
                    if (!determineJumps && logger.isDebugEnabled)
                        logger.debug("FUN {} \${}", `fun`.`fun`, String.format("%8x", `fun`.addr1).replace(' ', '0'))
                } else {
                    machineData.machineState!!.pc += rc
                    val `val` = machineData.apData.getLong(`fun`.addr1 * 8)
                    AtApiController.func1(`fun`.`fun`.toInt(), `val`, machineData)
                }
            }
        } else if (op == OpCode.E_OP_CODE_EXT_FUN_DAT_2) {
            rc = funAddrs

            if (rc == 0 || disassemble) {
                rc = 11

                if (disassemble) {
                    if (!determineJumps && logger.isDebugEnabled)
                        logger.debug("FUN {} \${} \${}", `fun`.`fun`, String.format("%8x", `fun`.addr3).replace(' ', '0'), String.format("%8x", `fun`.addr2).replace(' ', '0'))
                } else {
                    machineData.machineState!!.pc += rc
                    val val1 = machineData.apData!!.getLong(`fun`.addr3 * 8)
                    val val2 = machineData.apData!!.getLong(`fun`.addr2 * 8)

                    AtApiController.func2(`fun`.`fun`.toInt(), val1, val2, machineData)
                }
            }
        } else if (op == OpCode.E_OP_CODE_EXT_FUN_RET) {
            rc = funAddr

            if (rc == 0 || disassemble) {
                rc = 7

                if (disassemble) {
                    if (!determineJumps && logger.isDebugEnabled)
                        logger.debug("FUN @{} {}", String.format("%8x", `fun`.addr1).replace(' ', '0'), `fun`.`fun`)

                } else {
                    machineData.machineState!!.pc += rc

                    machineData.apData!!.putLong(`fun`.addr1 * 8, AtApiController.func(`fun`.`fun`.toInt(), machineData))
                    machineData.apData!!.clear()
                }
            }
        } else if (op == OpCode.E_OP_CODE_EXT_FUN_RET_DAT || op == OpCode.E_OP_CODE_EXT_FUN_RET_DAT_2) {
            rc = funAddrs
            val size = 10

            if ((rc == 0 || disassemble) && op == OpCode.E_OP_CODE_EXT_FUN_RET_DAT_2) {
                machineData.apCode.position(size)
                rc = getAddr(false)
                machineData.apCode.position(machineData.apCode.position() - size)
            }

            if (rc == 0) {
                rc = 1 + size + if (op == OpCode.E_OP_CODE_EXT_FUN_RET_DAT_2) 4 else 0

                if (disassemble) {
                    if (!determineJumps && logger.isDebugEnabled) {
                        logger.debug("FUN @{} {} \${}", String.format("%8x", `fun`.addr3).replace(' ', '0'), `fun`.`fun`, String.format("%8x", `fun`.addr2).replace(' ', '0'))
                        if (op == OpCode.E_OP_CODE_EXT_FUN_RET_DAT_2)
                            logger.debug(" \${}", String.format("%8x", `fun`.addr1).replace(' ', '0'))
                    }
                } else {
                    machineData.machineState!!.pc += rc
                    val `val` = machineData.apData!!.getLong(`fun`.addr2 * 8)

                    if (op != OpCode.E_OP_CODE_EXT_FUN_RET_DAT_2)
                        machineData.apData!!.putLong(`fun`.addr3 * 8, AtApiController.func1(`fun`.`fun`.toInt(), `val`, machineData))
                    else {
                        val val2 = machineData.apData!!.getLong(`fun`.addr1 * 8)
                        machineData.apData!!.putLong(`fun`.addr3 * 8, AtApiController.func2(`fun`.`fun`.toInt(), `val`, val2, machineData))
                    }
                    machineData.apData!!.clear()
                }
            }
        } else if (op == OpCode.E_OP_CODE_ERR_ADR) {
            getAddr(true) // rico666: Why getAddr if rc is set hard anyway ?? // TODO check if this updates the buffer or can be removed

            // don't check rc to allow for unsetting handler with -1
            rc = 5

            if (disassemble) {
                if (!determineJumps && logger.isDebugEnabled)
                    logger.debug("ERR :{}", String.format("%8x", `fun`.addr1))
            } else {
                if (`fun`.addr1 == -1 || machineData.machineState!!.jumps.contains(`fun`.addr1)) {
                    machineData.machineState!!.pc += rc
                    machineData.machineState!!.err = `fun`.addr1
                } else
                    rc = -2
            }
        } else if (!disassemble) {
            rc = -2
        }

        if (rc == -1 && disassemble && !determineJumps)
            logger.debug("\n(overflow)")

        if (rc == -2 && disassemble && !determineJumps)
            logger.debug("\n(invalid op)")

        return rc
    }

    private inner class Fun {
        internal var `fun`: Short = 0
        internal var addr1: Int = 0
        internal var addr2: Int = 0
        internal var `val`: Long = 0
        internal var off: Byte = 0
        internal var addr3: Int = 0
    }
}
