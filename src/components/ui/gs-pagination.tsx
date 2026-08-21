"use client";

import { useState } from "react";
import { Button, InputNumber, Pagination, type PaginationProps } from "antd";

export function GsPagination({ hideOnSinglePage = false, showSizeChanger = true, showQuickJumper = true, ...props }: PaginationProps) {
  const current = props.current ?? props.defaultCurrent ?? 1;
  const pageSize = props.pageSize ?? props.defaultPageSize ?? 10;
  const totalPages = Math.max(1, Math.ceil((props.total ?? 0) / pageSize));
  const disabled = props.disabled ?? false;
  const [jumpPage, setJumpPage] = useState(current);

  function changePage(nextPage: number, nextPageSize: number) {
    setJumpPage(nextPage);
    props.onChange?.(nextPage, nextPageSize);
  }

  function jump() {
    const nextPage = Math.min(totalPages, Math.max(1, jumpPage || 1));
    changePage(nextPage, pageSize);
  }

  return (
    <div className="gsPagination">
      <Button size="small" disabled={disabled || current <= 1} onClick={() => changePage(1, pageSize)}>首页</Button>
      <Pagination
        {...props}
        hideOnSinglePage={hideOnSinglePage}
        onChange={changePage}
        showQuickJumper={false}
        showSizeChanger={showSizeChanger}
      />
      <Button size="small" disabled={disabled || current >= totalPages} onClick={() => changePage(totalPages, pageSize)}>尾页</Button>
      {showQuickJumper !== false ? <div className="gsPaginationJump"><span>跳至</span><InputNumber aria-label={`跳转页码，共 ${totalPages} 页`} controls={false} min={1} max={totalPages} size="small" value={jumpPage} onChange={(value) => setJumpPage(value ?? 1)} onPressEnter={jump} /><span>页</span><Button size="small" disabled={disabled} onClick={jump}>跳转</Button></div> : null}
    </div>
  );
}
