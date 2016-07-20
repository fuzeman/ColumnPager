package net.dgardiner.viewpagerex.core;

public class PagerItem {
    private int position;
    private int index;

    private Object object;

    private int offset;
    private int width;

    private boolean scrolling;

    public PagerItem() {

    }

    public PagerItem(int position, int index) {
        this.position = position;
        this.index = index;
    }

    //
    // Properties
    //

    public int getPosition() {
        return position;
    }

    public void setPosition(int value) {
        position = value;
    }

    public int getIndex() {
        return index;
    }

    public void setIndex(int value) {
        index = value;
    }

    public Object getObject() {
        return object;
    }

    public void setObject(Object value) {
        object = value;
    }

    public int getOffset() {
        return offset;
    }

    public void setOffset(int value) {
        offset = value;
    }

    public int getWidth() {
        return width;
    }

    public void setWidth(int value) {
        width = value;
    }

    public boolean isScrolling() {
        return scrolling;
    }

    public void setScrolling(boolean value) {
        scrolling = value;
    }

    //
    // Public methods
    //

    @Override
    public String toString() {
        return "<PagerItem index: " + index + ", object: " + object + ", offset: " + offset + ", width: " + width + ">";
    }
}
