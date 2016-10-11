package com.ineat.quickadapter;

import android.support.annotation.LayoutRes;
import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Created by mslimani on 08/10/2016.
 */
public class QuickAdapter<ITEM> extends RecyclerView.Adapter<QuickItemRenderer> {

    private final SparseArray<Class<? extends QuickItemRenderer<ITEM>>> mViewTypeSparseArray;
    // used for trace exception
    private final SparseArray<Class<? extends QuickItemRenderer<ITEM>>> mCacheHashClassSparseArray;
    private QuickAdapterTypeFactory<ITEM, QuickItemRenderer<ITEM>> mQuickAdapterTypeFactory;
    private List<ITEM> mItems;
    private boolean mAutoNotify;
    private RecyclerView mRecyclerView;
    private OnItemClickListener<ITEM> mOnItemClickListener;
    private RecyclerView.OnChildAttachStateChangeListener mOnChildAttachStateChangeListener;
    private Class<? extends HeaderQuickItemRenderer> mHeaderItemRenderer;
    private Class<? extends FooterQuickItemRenderer> mFooterItemRenderer;

    public QuickAdapter() {
        mViewTypeSparseArray = new SparseArray<>();
        mCacheHashClassSparseArray = new SparseArray<>();
        mItems = new ArrayList<>();
    }

    public QuickAdapter(@NonNull List<ITEM> items) {
        this();
        mItems = items;
    }

    public QuickAdapter(@NonNull List<ITEM> items, QuickAdapterTypeFactory<ITEM, QuickItemRenderer<ITEM>>
            quickAdapterTypeFactory) {
        this(items);
        mQuickAdapterTypeFactory = quickAdapterTypeFactory;
    }

    public void setQuickAdapterTypeFactory(QuickAdapterTypeFactory<ITEM, QuickItemRenderer<ITEM>>
                                                   quickAdapterTypeFactory) {
        mQuickAdapterTypeFactory = quickAdapterTypeFactory;
    }

    public void setOnItemClickListener(OnItemClickListener<ITEM> onItemClickListener) {
        mOnItemClickListener = onItemClickListener;
        attachListeners();
    }

    public boolean isAutoNotify() {
        return mAutoNotify;
    }

    public void setAutoNotify(boolean autoNotify) {
        mAutoNotify = autoNotify;
    }

    // region register

    public void registerHolder(Class<? extends QuickItemRenderer<ITEM>> holderClass) {
        if (!holderClass.isAnnotationPresent(QuickLayout.class)) {
            throw new IllegalArgumentException(holderClass + " is not annoted by " + QuickLayout
                    .class.getSimpleName());
        }

        final QuickLayout quickLayout = holderClass.getAnnotation(QuickLayout.class);
        registerType(getType(holderClass), holderClass);
    }

    public void registerHeader(Class<? extends HeaderQuickItemRenderer> headerItemRenderer) {
        mHeaderItemRenderer = headerItemRenderer;
        if (mRecyclerView != null) {
            notifyDataSetChanged();
        }
    }

    public void registerFooter(Class<? extends FooterQuickItemRenderer> footerItemRenderer) {
        mFooterItemRenderer = footerItemRenderer;
        if (mRecyclerView != null) {
            notifyDataSetChanged();
        }
    }

    // endregion register

    // region items

    public ITEM getItemAtPosition(int position) {
        final int count = getItemCount();
        if (count == 0 || position >= count) {
            return null;
        }
        return mItems.get(position);
    }

    public void setItems(List<ITEM> items) {
        mItems = items;
        if (mAutoNotify) {
            notifyDataSetChanged();
        }
    }

    public void addItem(@NonNull ITEM item) {
        int position = getItemCount();
        addItem(position, item);
    }

    public void addItem(int position, @NonNull ITEM item) {
        mItems.add(position, item);
        if (mAutoNotify) {
            notifyItemInserted(position);
        }
    }

    public void addItems(@NonNull List<ITEM> items) {
        final int count = getItemCount();
        mItems.addAll(items);
        if (mAutoNotify) {
            notifyItemRangeInserted(count, items.size());
        }
    }

    public void swap(int first, int end) {
        Collections.swap(mItems, first, end);
        if (mAutoNotify) {
            notifyItemMoved(first, end);
        }
    }

    public void removeItem(int position) {
        mItems.remove(position);
        if (mAutoNotify) {
            notifyItemRemoved(position);
        }
    }

    public void removeItems() {
        final int count = getItemCount();
        if (count == 0) {
            return;
        }

        mItems.clear();

        if (mAutoNotify) {
            for (int i = count - 1; i > 0; i--) {
                notifyItemRangeRemoved(0, count);
            }
        }
    }

    // endregion items

    @Override
    public void onAttachedToRecyclerView(RecyclerView recyclerView) {
        super.onAttachedToRecyclerView(recyclerView);
        mRecyclerView = recyclerView;
        attachListeners();
    }

    @Override
    public void onDetachedFromRecyclerView(RecyclerView recyclerView) {
        super.onDetachedFromRecyclerView(recyclerView);
        detachListeners();
        mRecyclerView = null;
    }

    @Override
    public int getItemViewType(int position) {
        if (mQuickAdapterTypeFactory == null) {
            throw new IllegalArgumentException("QuickAdapterTypeFactory not exist, call method : " +
                    "setQuickAdapterTypeFactory(...)");
        }

        if (isHeaderItem(position)) {
            return getType(mHeaderItemRenderer);
        }

        if (isFooterItem(position)) {
            return getType(mFooterItemRenderer);
        }

        Class<? extends QuickItemRenderer<ITEM>> holderClass = mQuickAdapterTypeFactory.getType
                (position, getItemAtPosition(getContentPosition(position)));
        int type = getType(holderClass);
        mCacheHashClassSparseArray.put(type, holderClass);
        return type;
    }

    @Override
    public QuickItemRenderer onCreateViewHolder(ViewGroup parent, int viewType) {
        final LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        Class<? extends QuickItemRenderer> holderClass;


        if (mHeaderItemRenderer != null && viewType == getType(mHeaderItemRenderer)) {
            // is header
            holderClass = mHeaderItemRenderer;
        } else if (mFooterItemRenderer != null && viewType == getType(mFooterItemRenderer)) {
            // is footer
            holderClass = mFooterItemRenderer;
        } else {
            // is content
            holderClass = mViewTypeSparseArray.get(viewType);
        }

        if (holderClass == null) {
            throw new IllegalArgumentException(mCacheHashClassSparseArray.get(viewType)
                    .getSimpleName() + " is not registered");
        }

        QuickLayout quickLayout = holderClass.getAnnotation(QuickLayout.class);
        if (quickLayout == null) {
            throw new IllegalArgumentException(holderClass + " is not annoted by " + QuickLayout
                    .class.getSimpleName());
        }

        @LayoutRes int layoutRes = quickLayout.value();
        final View view = inflater.inflate(layoutRes, parent, false);
        QuickItemRenderer holder;
        try {
            Constructor<? extends QuickItemRenderer> constructor = holderClass
                    .getDeclaredConstructor(View.class);
            // accessible private, private package, protected constructor
            constructor.setAccessible(true);
            holder = constructor.newInstance(view);
        } catch (Exception e) {
            boolean isStatic = Modifier.isStatic(holderClass.getModifiers());
            boolean isInner = holderClass.getEnclosingClass() != null;
            if (isInner && !isStatic) {
                // declare class to [modifier] static class { ...
                throw new IllegalArgumentException(holderClass.getSimpleName() + " is an inner " +
                        "class and is not static.");
            }

            throw new IllegalArgumentException(holderClass.getSimpleName() + " doit être " +
                    "accessible ainsi qu'un seul constructeur public avec un seul paramètre " +
                    "Holder(android.view.View view)");
        }
        return holder;
    }

    @SuppressWarnings("unchecked")
    @Override
    public void onBindViewHolder(QuickItemRenderer holder, int position) {
        if (holder instanceof HeaderFooterQuickItemRenderer) {
            ((HeaderFooterQuickItemRenderer) holder).onBind();
        } else {
            final ITEM item = getItemAtPosition(getContentPosition(position));
            holder.onBind(position, item);
        }
    }

    @Override
    public int getItemCount() {
        return mItems.size() + getHeaderFooterSize();
    }

    private int getContentPosition(int position) {
        return mHeaderItemRenderer != null ? position - 1 : position;
    }

    private boolean isHeaderItem(int position) {
        return position == 0 && mHeaderItemRenderer != null;
    }

    private boolean isFooterItem(int position) {
        return position == (getItemCount() - 1) && mFooterItemRenderer != null;
    }

    private int getHeaderFooterSize() {
        int count = 0;
        if (mHeaderItemRenderer != null) {
            count++;
        }

        if (mFooterItemRenderer != null) {
            count++;
        }
        return count;
    }

    private void detachListeners() {
        if (mRecyclerView == null || mOnChildAttachStateChangeListener == null) {
            return;
        }
        mRecyclerView.removeOnChildAttachStateChangeListener(mOnChildAttachStateChangeListener);
    }

    private void attachListeners() {
        if (mRecyclerView == null || mOnItemClickListener == null) {
            return;
        }

        if (mOnChildAttachStateChangeListener == null) {
            mOnChildAttachStateChangeListener = new RecyclerView.OnChildAttachStateChangeListener() {
                @Override
                public void onChildViewAttachedToWindow(final View view) {
                    view.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            int position = mRecyclerView.getChildAdapterPosition(v);
                            RecyclerView.ViewHolder holder = mRecyclerView
                                    .findViewHolderForAdapterPosition(position);
                            if (!(holder instanceof HeaderFooterQuickItemRenderer)) {
                                mOnItemClickListener.onItemClick(mRecyclerView, v, position,
                                        getItemAtPosition(getContentPosition(position)));
                            }
                        }
                    });
                }

                @Override
                public void onChildViewDetachedFromWindow(View view) {
                    if (view == null) {
                        return;
                    }
                    view.setOnClickListener(null);
                }
            };
            mRecyclerView.addOnChildAttachStateChangeListener(mOnChildAttachStateChangeListener);
        }


    }

    private void registerType(int type, Class<? extends QuickItemRenderer<ITEM>> holderClass) {
        mViewTypeSparseArray.put(type, holderClass);
        if (mAutoNotify) {
            notifyDataSetChanged();
        }
    }

    private int getType(Class<? extends IQuickItemRenderer<?>> holderClass) {
        return holderClass.getName().hashCode();
    }

    public interface QuickAdapterTypeFactory<ITEM, HOLDER> {
        Class<? extends HOLDER> getType(int position, ITEM item);
    }

    public interface OnItemClickListener<ITEM> {
        void onItemClick(RecyclerView recyclerView, View view, int position, ITEM item);
    }

}