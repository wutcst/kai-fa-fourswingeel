package com.slaythespire.model;

import java.util.List;
import java.util.Map;

/**
 * 事件数据模板，对应 events.json 中的每个事件
 */
public class EventTemplate {
    private String id;
    private String title;
    private String description;
    private String background;
    private String icon;
    private List<EventOption> options;
    private List<Integer> acts;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getBackground() { return background; }
    public void setBackground(String background) { this.background = background; }
    public String getIcon() { return icon; }
    public void setIcon(String icon) { this.icon = icon; }
    public List<EventOption> getOptions() { return options; }
    public void setOptions(List<EventOption> options) { this.options = options; }
    public List<Integer> getActs() { return acts; }
    public void setActs(List<Integer> acts) { this.acts = acts; }

    public static class EventOption {
        private String text;
        private List<Map<String, Object>> effects;

        public String getText() { return text; }
        public void setText(String text) { this.text = text; }
        public List<Map<String, Object>> getEffects() { return effects; }
        public void setEffects(List<Map<String, Object>> effects) { this.effects = effects; }
    }
}
