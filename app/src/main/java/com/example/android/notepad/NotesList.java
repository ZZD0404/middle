/*
 * Copyright (C) 2007 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.notepad;

import com.example.android.notepad.NotePad;

import android.app.AlertDialog;
import android.app.ListActivity;
import android.content.ClipboardManager;
import android.content.ClipData;
import android.content.ComponentName;
import android.content.ContentUris;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ContextMenu.ContextMenuInfo;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;


/**
 * 笔记列表界面。在原版 NotePad 基础上扩展：
 * 1. 列表条目显示笔记的修改时间戳；
 * 2. 顶部搜索框按标题或内容实时查询；
 * 3. 支持按修改时间 / 创建时间 / 标题排序。
 */
public class NotesList extends ListActivity {

    private static final String TAG = "NotesList";

    /** 列表查询所需列 */
    private static final String[] PROJECTION = new String[] {
            NotePad.Notes._ID,                              // 0
            NotePad.Notes.COLUMN_NAME_TITLE,                // 1
            NotePad.Notes.COLUMN_NAME_NOTE,                 // 2
            NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE,    // 3
            NotePad.Notes.COLUMN_NAME_CREATE_DATE,          // 4
    };

    private static final int COLUMN_INDEX_TITLE = 1;
    private static final int COLUMN_INDEX_MODIFIED = 3;

    /** 三种排序方式 */
    private static final String SORT_MODIFIED =
            NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE + " DESC";
    private static final String SORT_CREATED =
            NotePad.Notes.COLUMN_NAME_CREATE_DATE + " DESC";
    private static final String SORT_TITLE =
            NotePad.Notes.COLUMN_NAME_TITLE + " COLLATE LOCALIZED ASC";

    private static final String[] SORT_ORDERS = { SORT_MODIFIED, SORT_CREATED, SORT_TITLE };

    private EditText mSearchBox;
    private TextView mEmptyView;
    private SimpleCursorAdapter mAdapter;
    private String mSortOrder = SORT_MODIFIED;
    private int mSortSelection = 0;

    /** 时间戳格式化器 */
    private final SimpleDateFormat mDateFormat =
            new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setDefaultKeyMode(DEFAULT_KEYS_SHORTCUT);

        Intent intent = getIntent();
        if (intent.getData() == null) {
            intent.setData(NotePad.Notes.CONTENT_URI);
        }

        // 加载带搜索栏的列表布局
        setContentView(R.layout.noteslist);
        mSearchBox = (EditText) findViewById(R.id.search_box);
        mEmptyView = (TextView) findViewById(android.R.id.empty);

        getListView().setOnCreateContextMenuListener(this);

        // 列表适配器：标题绑定 text1，时间戳绑定 text2
        String[] dataColumns = {
                NotePad.Notes.COLUMN_NAME_TITLE,
                NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE
        };
        int[] viewIDs = { android.R.id.text1, R.id.text2 };

        mAdapter = new SimpleCursorAdapter(
                this, R.layout.noteslist_item, null, dataColumns, viewIDs);

        // 把毫秒时间戳格式化为可读时间
        mAdapter.setViewBinder(new SimpleCursorAdapter.ViewBinder() {
            @Override
            public boolean setViewValue(View view, Cursor cursor, int columnIndex) {
                if (view.getId() == R.id.text2) {
                    long time = cursor.getLong(columnIndex);
                    TextView tv = (TextView) view;
                    if (time > 0) {
                        tv.setText(mDateFormat.format(new Date(time)));
                    } else {
                        tv.setText("");
                    }
                    return true;
                }
                return false;
            }
        });

        setListAdapter(mAdapter);

        // 搜索框文本变化时实时查询
        mSearchBox.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                requery();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        requery();
    }

    /** 根据当前关键字与排序方式重新查询，并刷新列表 */
    private void requery() {
        String keyword = mSearchBox == null ? "" : mSearchBox.getText().toString().trim();
        String selection;
        String[] selectionArgs;

        if (TextUtils.isEmpty(keyword)) {
            selection = null;
            selectionArgs = null;
            mEmptyView.setText(R.string.empty_list);
        } else {
            selection = NotePad.Notes.COLUMN_NAME_TITLE + " LIKE ? OR "
                    + NotePad.Notes.COLUMN_NAME_NOTE + " LIKE ?";
            selectionArgs = new String[] { "%" + keyword + "%", "%" + keyword + "%" };
            mEmptyView.setText(R.string.empty_search);
        }

        Cursor cursor = getContentResolver().query(
                getIntent().getData(),
                PROJECTION,
                selection,
                selectionArgs,
                mSortOrder
        );

        mAdapter.swapCursor(cursor);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.list_options_menu, menu);

        Intent intent = new Intent(null, getIntent().getData());
        intent.addCategory(Intent.CATEGORY_ALTERNATIVE);
        menu.addIntentOptions(Menu.CATEGORY_ALTERNATIVE, 0, 0,
                new ComponentName(this, NotesList.class), null, intent, 0, null);

        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);

        ClipboardManager clipboard = (ClipboardManager)
                getSystemService(Context.CLIPBOARD_SERVICE);

        MenuItem mPasteItem = menu.findItem(R.id.menu_paste);
        if (clipboard.hasPrimaryClip()) {
            mPasteItem.setEnabled(true);
        } else {
            mPasteItem.setEnabled(false);
        }

        final boolean haveItems = getListAdapter().getCount() > 0;
        if (haveItems) {
            Uri uri = ContentUris.withAppendedId(getIntent().getData(), getSelectedItemId());
            Intent[] specifics = new Intent[1];
            MenuItem[] items = new MenuItem[1];
            Intent intent = new Intent(null, uri);
            intent.addCategory(Intent.CATEGORY_ALTERNATIVE);

            menu.addIntentOptions(
                    Menu.CATEGORY_ALTERNATIVE,
                    Menu.NONE, Menu.NONE, null,
                    specifics, intent, Menu.NONE, items);

            if (items[0] != null) {
                items[0].setShortcut('1', 'e');
            }
        } else {
            menu.removeGroup(Menu.CATEGORY_ALTERNATIVE);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.menu_add) {
            startActivity(new Intent(Intent.ACTION_INSERT, getIntent().getData()));
            return true;
        } else if (id == R.id.menu_paste) {
            startActivity(new Intent(Intent.ACTION_PASTE, getIntent().getData()));
            return true;
        } else if (id == R.id.menu_sort) {
            showSortDialog();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /** 弹出排序方式选择对话框 */
    private void showSortDialog() {
        final String[] labels = {
                getString(R.string.sort_modified),
                getString(R.string.sort_created),
                getString(R.string.sort_title)
        };
        new AlertDialog.Builder(this)
                .setTitle(R.string.menu_sort)
                .setSingleChoiceItems(labels, mSortSelection,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                mSortSelection = which;
                                mSortOrder = SORT_ORDERS[which];
                                dialog.dismiss();
                                requery();
                            }
                        })
                .show();
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View view, ContextMenuInfo menuInfo) {
        AdapterView.AdapterContextMenuInfo info;
        try {
            info = (AdapterView.AdapterContextMenuInfo) menuInfo;
        } catch (ClassCastException e) {
            Log.e(TAG, "bad menuInfo", e);
            return;
        }

        Cursor cursor = (Cursor) getListAdapter().getItem(info.position);
        if (cursor == null) {
            return;
        }

        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.list_context_menu, menu);
        menu.setHeaderTitle(cursor.getString(COLUMN_INDEX_TITLE));

        Intent intent = new Intent(null, Uri.withAppendedPath(getIntent().getData(),
                Integer.toString((int) info.id)));
        intent.addCategory(Intent.CATEGORY_ALTERNATIVE);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        menu.addIntentOptions(Menu.CATEGORY_ALTERNATIVE, 0, 0,
                new ComponentName(this, NotesList.class), null, intent, 0, null);
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        AdapterView.AdapterContextMenuInfo info;
        try {
            info = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
        } catch (ClassCastException e) {
            Log.e(TAG, "bad menuInfo", e);
            return false;
        }
        Uri noteUri = ContentUris.withAppendedId(getIntent().getData(), info.id);

        int id = item.getItemId();
        if (id == R.id.context_open) {
            startActivity(new Intent(Intent.ACTION_EDIT, noteUri));
            return true;
        } else if (id == R.id.context_copy) {
            ClipboardManager clipboard = (ClipboardManager)
                    getSystemService(Context.CLIPBOARD_SERVICE);
            clipboard.setPrimaryClip(ClipData.newUri(
                    getContentResolver(), "Note", noteUri));
            return true;
        } else if (id == R.id.context_delete) {
            getContentResolver().delete(noteUri, null, null);
            return true;
        }
        return super.onContextItemSelected(item);
    }

    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        Uri uri = ContentUris.withAppendedId(getIntent().getData(), id);
        String action = getIntent().getAction();

        if (Intent.ACTION_PICK.equals(action) || Intent.ACTION_GET_CONTENT.equals(action)) {
            setResult(RESULT_OK, new Intent().setData(uri));
        } else {
            startActivity(new Intent(Intent.ACTION_EDIT, uri));
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mAdapter != null) {
            mAdapter.swapCursor(null);
        }
    }
}
