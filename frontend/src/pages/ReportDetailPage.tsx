import { useState, useEffect, useCallback, useRef, useMemo } from 'react';
import { useParams, useNavigate, Link } from 'react-router-dom';
import { PageHeader } from '@/components/layout/PageHeader';
import { StatusBadge } from '@/components/shared/StatusBadge';
import { ConfirmDialog } from '@/components/shared/ConfirmDialog';
import { KeyboardShortcutsHelp, type KeyboardShortcut } from '@/components/shared/KeyboardShortcutsHelp';
import { ItemDetailPanel } from '@/components/shared/ItemDetailPanel';
import { ReportNewsItemRow } from '@/components/reports/ReportNewsItemRow';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Skeleton } from '@/components/ui/skeleton';
import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/ui/tooltip';
import { ArrowLeft, Calendar, ListOrdered, Trash2, Keyboard } from 'lucide-react';
import { format, formatDistanceToNow } from 'date-fns';
import { parseApiDate } from '@/lib/utils';
import { apiClient } from '@/lib/api';
import { toast } from '@/hooks/use-toast';
import { useKeyboardNavigation } from '@/hooks/use-keyboard-navigation';
import type { DailyReportDTO, ReportItemDTO, NewsItemDTO } from '@/types';

const REPORT_ITEM_SHORTCUTS: KeyboardShortcut[] = [
  { key: 'j', description: 'Move to next item' },
  { key: 'k', description: 'Move to previous item' },
  { key: 'Enter / o', description: 'Open selected item' },
  { key: 'x / Esc', description: 'Close panel' },
  { key: 'e', description: 'Open external link' },
  { key: 'r', description: 'Toggle read' },
  { key: 's', description: 'Toggle saved' },
  { key: '?', description: 'Toggle this help' },
];

export default function ReportDetailPage() {
  const { id, reportId } = useParams<{ id: string; reportId: string }>();
  const navigate = useNavigate();
  const [report, setReport] = useState<DailyReportDTO | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [isDeleting, setIsDeleting] = useState(false);
  const [showDeleteDialog, setShowDeleteDialog] = useState(false);
  const [selectedIndex, setSelectedIndex] = useState(-1);
  const [isPanelOpen, setIsPanelOpen] = useState(false);
  const [showHelp, setShowHelp] = useState(false);
  const [selectedNewsItem, setSelectedNewsItem] = useState<NewsItemDTO | null>(null);
  const [isLoadingNewsItem, setIsLoadingNewsItem] = useState(false);
  const itemRefs = useRef<(HTMLDivElement | null)[]>([]);

  const fetchReport = useCallback(async () => {
    setIsLoading(true);
    try {
      const data = await apiClient.getDayBriefReport(id!, reportId!);
      setReport(data);
    } catch (error: unknown) {
      toast({
        title: 'Failed to load report',
        description: error instanceof Error ? error.message : 'An error occurred',
        variant: 'destructive',
      });
      navigate(`/briefs/${id}/reports`);
    } finally {
      setIsLoading(false);
    }
  }, [id, reportId, navigate]);

  useEffect(() => {
    if (id && reportId) {
      fetchReport();
    }
  }, [id, reportId, fetchReport]);

  // Sort items by position once
  const sortedItems = useMemo(
    () => report?.items.slice().sort((a, b) => a.position - b.position) ?? [],
    [report?.items]
  );

  // Fetch full news item when selection changes and panel is open
  const fetchNewsItem = useCallback(async (newsItemId: string) => {
    setIsLoadingNewsItem(true);
    try {
      const newsItem = await apiClient.getNewsItem(newsItemId);
      setSelectedNewsItem(newsItem);
    } catch (error: unknown) {
      toast({
        title: 'Failed to load item details',
        description: error instanceof Error ? error.message : 'An error occurred',
        variant: 'destructive',
      });
      setSelectedNewsItem(null);
    } finally {
      setIsLoadingNewsItem(false);
    }
  }, []);

  // Fetch news item when panel opens or selection changes while panel is open
  useEffect(() => {
    if (isPanelOpen && selectedIndex >= 0 && selectedIndex < sortedItems.length) {
      const reportItem = sortedItems[selectedIndex];
      // Only fetch if we don't already have this item or it's a different item
      if (!selectedNewsItem || selectedNewsItem.id !== reportItem.newsItemId) {
        fetchNewsItem(reportItem.newsItemId);
      }
    } else if (!isPanelOpen) {
      setSelectedNewsItem(null);
    }
  }, [isPanelOpen, selectedIndex, sortedItems, selectedNewsItem, fetchNewsItem]);

  const handleDelete = async () => {
    setIsDeleting(true);
    try {
      await apiClient.deleteDayBriefReport(id!, reportId!);
      toast({
        title: 'Report deleted',
        description: 'The report has been removed.',
      });
      navigate(`/briefs/${id}`);
    } catch (error: unknown) {
      toast({
        title: 'Failed to delete report',
        description: error instanceof Error ? error.message : 'An error occurred',
        variant: 'destructive',
      });
    } finally {
      setIsDeleting(false);
      setShowDeleteDialog(false);
    }
  };

  const handleRowClick = (index: number) => {
    setSelectedIndex(index);
    setIsPanelOpen(true);
  };

  const handleClosePanel = () => {
    setIsPanelOpen(false);
  };

  const handleSelect = useCallback((index: number) => {
    setSelectedIndex(index);
    itemRefs.current[index]?.scrollIntoView({ block: 'nearest', behavior: 'smooth' });
  }, []);

  const handleOpen = useCallback(() => {
    if (selectedIndex >= 0 && selectedIndex < sortedItems.length) {
      setIsPanelOpen(true);
    }
  }, [selectedIndex, sortedItems.length]);

  const handleExternalLink = useCallback(() => {
    if (selectedIndex >= 0 && selectedIndex < sortedItems.length) {
      window.open(sortedItems[selectedIndex].link, '_blank', 'noopener,noreferrer');
    }
  }, [selectedIndex, sortedItems]);

  const handleExternalLinkClick = (e: React.MouseEvent, item: ReportItemDTO) => {
    e.stopPropagation();
    window.open(item.link, '_blank', 'noopener,noreferrer');
  };

  const handleNewsItemUpdated = useCallback((updatedItem: NewsItemDTO) => {
    setSelectedNewsItem(updatedItem);
  }, []);

  const handleToggleRead = useCallback(() => {
    if (selectedNewsItem) {
      apiClient.toggleRead(selectedNewsItem.id).then(handleNewsItemUpdated).catch(console.error);
    }
  }, [selectedNewsItem, handleNewsItemUpdated]);

  const handleToggleSaved = useCallback(() => {
    if (selectedNewsItem) {
      apiClient.toggleSaved(selectedNewsItem.id).then(handleNewsItemUpdated).catch(console.error);
    }
  }, [selectedNewsItem, handleNewsItemUpdated]);

  useKeyboardNavigation({
    itemCount: sortedItems.length,
    selectedIndex,
    onSelect: handleSelect,
    onOpen: handleOpen,
    onClose: handleClosePanel,
    onExternalLink: handleExternalLink,
    onToggleHelp: () => setShowHelp((prev) => !prev),
    onToggleRead: handleToggleRead,
    onToggleSaved: handleToggleSaved,
    isPanelOpen,
    disabled: isLoading || isLoadingNewsItem,
  });

  if (isLoading) {
    return (
      <>
        <PageHeader title="Loading..." />
        <main className="flex-1 p-4">
          <Skeleton className="h-64" />
        </main>
      </>
    );
  }

  if (!report) {
    return null;
  }

  return (
    <>
      <PageHeader title={report.dayBriefTitle}>
        <Link to={`/briefs/${id}/reports`}>
          <Button variant="ghost" size="sm">
            <ArrowLeft className="mr-2 h-4 w-4" />
            Back to Reports
          </Button>
        </Link>
      </PageHeader>

      <main className="flex-1 p-4 space-y-4">
        {/* Report Header */}
        <Card>
          <CardHeader>
            <div className="flex items-center justify-between">
              <CardTitle className="flex items-center gap-2">
                {format(new Date(report.generatedAt), 'EEEE, MMMM d, yyyy')}
                <StatusBadge status={report.status} />
              </CardTitle>
              <Button
                variant="outline"
                size="sm"
                className="text-destructive hover:text-destructive"
                onClick={() => setShowDeleteDialog(true)}
              >
                <Trash2 className="mr-2 h-4 w-4" />
                Delete
              </Button>
            </div>
          </CardHeader>
          <CardContent>
            <div className="flex items-center gap-6 text-sm text-muted-foreground">
              <Tooltip>
                <TooltipTrigger asChild>
                  <div className="flex items-center gap-1 cursor-default">
                    <Calendar className="h-4 w-4" />
                    <span>
                      Generated {formatDistanceToNow(parseApiDate(report.generatedAt), { addSuffix: true })}
                    </span>
                  </div>
                </TooltipTrigger>
                <TooltipContent>
                  {format(parseApiDate(report.generatedAt), 'PPpp')}
                </TooltipContent>
              </Tooltip>
              <div className="flex items-center gap-1">
                <ListOrdered className="h-4 w-4" />
                <span>{report.itemCount} items</span>
              </div>
            </div>
            {report.dayBriefDescription && (
              <p className="mt-4 text-sm text-muted-foreground">
                {report.dayBriefDescription}
              </p>
            )}
          </CardContent>
        </Card>

        {/* Report Items */}
        <Card>
          <CardContent className="p-0">
            {sortedItems.length === 0 ? (
              <div className="p-6 text-center">
                <p className="text-muted-foreground">No items in this report.</p>
              </div>
            ) : (
              <>
                <div className="max-h-[600px] overflow-y-auto">
                  {sortedItems.map((item, index) => (
                    <ReportNewsItemRow
                      key={item.id}
                      ref={(el) => {
                        itemRefs.current[index] = el;
                      }}
                      item={item}
                      isSelected={selectedIndex === index}
                      onClick={() => handleRowClick(index)}
                      onExternalLink={(e) => handleExternalLinkClick(e, item)}
                    />
                  ))}
                </div>
                <div className="p-4 border-t flex items-center justify-end">
                  <button
                    onClick={() => setShowHelp(true)}
                    className="flex items-center gap-1 text-xs text-muted-foreground hover:text-foreground transition-colors"
                  >
                    <Keyboard className="h-3 w-3" />
                    Press ? for shortcuts
                  </button>
                </div>
              </>
            )}
          </CardContent>
        </Card>
      </main>

      <ItemDetailPanel
        item={selectedNewsItem}
        open={isPanelOpen}
        onClose={handleClosePanel}
        onItemUpdated={handleNewsItemUpdated}
      />

      <KeyboardShortcutsHelp
        open={showHelp}
        onClose={() => setShowHelp(false)}
        shortcuts={REPORT_ITEM_SHORTCUTS}
      />

      <ConfirmDialog
        open={showDeleteDialog}
        onOpenChange={setShowDeleteDialog}
        title="Delete Report"
        description="Are you sure you want to delete this report? This action cannot be undone."
        confirmLabel="Delete"
        variant="destructive"
        onConfirm={handleDelete}
        isLoading={isDeleting}
      />
    </>
  );
}
