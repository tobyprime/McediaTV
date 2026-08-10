package top.tobyprime.mcedia_mtv_plugin.model;

/**
 * 播放器对其他玩家的控制权限级别，由 GUI 中的同一个按钮循环切换。
 * <p>
 * 替换旧的「公开/私有」与「允许他人控制播放」两个独立开关：
 * <ul>
 *     <li>{@link #PUBLIC}：其他玩家可控制播放并编辑播放器设置，仅不能删除播放器或修改此权限；</li>
 *     <li>{@link #CONTROL}：其他玩家可控制播放、切换频道，但不能编辑播放器设置；</li>
 *     <li>{@link #PRIVATE}：其他玩家仅可观看，不能控制。</li>
 * </ul>
 */
public enum ControlAccess {
    PUBLIC,
    CONTROL,
    PRIVATE;

    /** 按钮点击时的循环顺序：公开 → 仅控制 → 私有 → 公开。 */
    public ControlAccess next() {
        return switch (this) {
            case PUBLIC -> CONTROL;
            case CONTROL -> PRIVATE;
            case PRIVATE -> PUBLIC;
        };
    }
}
