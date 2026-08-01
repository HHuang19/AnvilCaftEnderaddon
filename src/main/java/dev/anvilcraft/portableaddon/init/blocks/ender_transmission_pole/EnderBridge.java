package dev.anvilcraft.portableaddon.init.blocks.ender_transmission_pole;

import dev.dubhe.anvilcraft.api.power.IPowerComponent;

/**
 * 跨维度电力桥元件的标记接口。
 * <p>
 * 实现该接口的元件在计算电网"纯富余量"时会被整体排除（含自身与其它链路），
 * 使上报的富余量与已施加的输电功率无关，从而避免振荡与跨链路串扰，
 * 同时天然抑制同一电网内的无意义自传输。
 */
public interface EnderBridge extends IPowerComponent {
}
