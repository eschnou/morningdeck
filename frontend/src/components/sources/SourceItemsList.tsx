import { useState, useEffect, useCallback, useRef } from 'react';
import { Newspaper, Keyboard, Bookmark, CheckCheck, Loader2 } from 'lucide-react';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Skeleton } from '@/components/ui/skeleton';
import { Button } from '@/components/ui/button';
import { ToggleGroup, ToggleGroupItem } from '@/components/ui/toggle-group';
import { PaginationControls } from '@/components/shared/PaginationControls';
import { EmptyState } from '@/components/shared/EmptyState';
import { ItemDetailPanel } from '@/components/shared/ItemDetailPanel';
import { KeyboardShortcutsHelp } from '@/components/shared/KeyboardShortcutsHelp';
import { NewsItemRow } from './NewsItemRow';
import { apiClient } from '@/lib/api';
import { toast } from '@/hooks/use-toast';
import { useKeyboardNavigation } from '@/hooks/use-keyboard-navigation';
import type { NewsItemDTO, ReadFilter } from '@/types';

interface SourceItemsListProps {
  sourceId: string;
  onRefresh?: () => void;
  onPrevSource?: () => void;
  onNextSource?: () => void;
}

export function SourceItemsList({ sourceId, onRefresh, onPrevSource, onNextSource }: SourceItemsListProps) {
  const [items, setItems] = useState<NewsItemDTO[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [currentPage, setCurrentPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [selectedIndex, setSelectedIndex] = useState(-1);
  const [isPanelOpen, setIsPanelOpen] = useState(false);
  const [showHelp, setShowHelp] = useState(false);
  const [readFilter, setReadFilter] = useState<ReadFilter>('ALL');
  const [savedFilter, setSavedFilter] = useState(false);
  const [isMarkingAllRead, setIsMarkingAllRead] = useState(false);
  const itemRefs = useRef<(HTMLDivElement | null)[]>([]);

  const fetchItems = useCallback(async (page = 0) => {
    setIsLoading(true);
    try {
      const response = await apiClient.getNewsItems(
        sourceId,
        page,
        20,
        readFilter,
        savedFilter || undefined
      );
      setItems(response.content);
      setTotalPages(response.totalPages);
      setTotalElements(response.totalElements);
      setCurrentPage(response.number);
      setSelectedIndex(-1);
    } catch (error: unknown) {
      toast({
        title: 'Failed to load items',
        description: error instanceof Error ? error.message : 'An error occurred',
        variant: 'destructive',
      });
    } finally {
      setIsLoading(false);
    }
  }, [sourceId, readFilter, savedFilter]);

  useEffect(() => {
    fetchItems(0);
  }, [fetchItems]);

  const handlePageChange = (page: number) => {
    fetchItems(page);
  };

  const handleRowClick = (index: number) => {
    setSelectedIndex(index);
    setIsPanelOpen(true);
  };

  const handleClosePanel = () => {
    setIsPanelOpen(false);
  };

  const handleItemUpdated = useCallback((updatedItem: NewsItemDTO) => {
    setItems((prevItems) =>
      prevItems.map((item) =>
        item.id === updatedItem.id ? updatedItem : item
      )
    );
  }, []);

  const handleSelect = useCallback((index: number) => {
    setSelectedIndex(index);
    // Scroll the selected item into view
    itemRefs.current[index]?.scrollIntoView({ block: 'nearest', behavior: 'smooth' });
  }, []);

  const handleOpen = useCallback(() => {
    if (selectedIndex >= 0 && selectedIndex < items.length) {
      setIsPanelOpen(true);
    }
  }, [selectedIndex, items.length]);

  const handleKeyboardExternalLink = useCallback(() => {
    if (selectedIndex >= 0 && selectedIndex < items.length) {
      window.open(items[selectedIndex].link, '_blank', 'noopener,noreferrer');
    }
  }, [selectedIndex, items]);

  const handleKeyboardToggleRead = useCallback(() => {
    if (selectedIndex >= 0 && selectedIndex < items.length) {
      const item = items[selectedIndex];
      apiClient.toggleRead(item.id).then(handleItemUpdated).catch(console.error);
    }
  }, [selectedIndex, items, handleItemUpdated]);

  const handleKeyboardToggleSaved = useCallback(() => {
    if (selectedIndex >= 0 && selectedIndex < items.length) {
      const item = items[selectedIndex];
      apiClient.toggleSaved(item.id).then(handleItemUpdated).catch(console.error);
    }
  }, [selectedIndex, items, handleItemUpdated]);

  useKeyboardNavigation({
    itemCount: items.length,
    selectedIndex,
    onSelect: handleSelect,
    onOpen: handleOpen,
    onClose: handleClosePanel,
    onExternalLink: handleKeyboardExternalLink,
    onToggleHelp: () => setShowHelp((prev) => !prev),
    onToggleRead: handleKeyboardToggleRead,
    onToggleSaved: handleKeyboardToggleSaved,
    onPrevSource,
    onNextSource,
    isPanelOpen,
    disabled: isLoading,
  });

  const handleExternalLink = (e: React.MouseEvent, item: NewsItemDTO) => {
    e.stopPropagation();
    window.open(item.link, '_blank', 'noopener,noreferrer');
  };

  const handleMarkAllAsRead = async () => {
    setIsMarkingAllRead(true);
    try {
      const result = await apiClient.markAllReadBySource(sourceId);
      toast({
        title: 'Marked as read',
        description: `${result.updatedCount} items marked as read`,
      });
      fetchItems(currentPage);
    } catch (error: unknown) {
      toast({
        title: 'Failed to mark items as read',
        description: error instanceof Error ? error.message : 'An error occurred',
        variant: 'destructive',
      });
    } finally {
      setIsMarkingAllRead(false);
    }
  };

  const getPageInfo = () => {
    if (totalElements === 0) return null;
    const start = currentPage * 20 + 1;
    const end = Math.min((currentPage + 1) * 20, totalElements);
    return `Showing ${start}-${end} of ${totalElements} items`;
  };

  return (
    <Card className="mt-6">
      <CardHeader>
        <div className="flex items-center justify-between">
          <CardTitle className="text-lg">Items</CardTitle>
          {!isLoading && totalElements > 0 && (
            <span className="text-sm text-muted-foreground">{getPageInfo()}</span>
          )}
        </div>
        <div className="flex items-center gap-4 pt-2">
          <ToggleGroup
            type="single"
            value={readFilter}
            onValueChange={(value) => value && setReadFilter(value as ReadFilter)}
            size="sm"
          >
            <ToggleGroupItem value="ALL" aria-label="Show all items">
              All
            </ToggleGroupItem>
            <ToggleGroupItem value="UNREAD" aria-label="Show unread items">
              Unread
            </ToggleGroupItem>
            <ToggleGroupItem value="READ" aria-label="Show read items">
              Read
            </ToggleGroupItem>
          </ToggleGroup>
          <Button
            variant={savedFilter ? 'default' : 'outline'}
            size="sm"
            onClick={() => setSavedFilter(!savedFilter)}
            className="gap-1"
          >
            <Bookmark className={`h-4 w-4 ${savedFilter ? 'fill-current' : ''}`} />
            Saved
          </Button>
          <Button
            variant="outline"
            size="sm"
            onClick={handleMarkAllAsRead}
            disabled={isMarkingAllRead || isLoading || items.length === 0}
            className="gap-1"
          >
            {isMarkingAllRead ? (
              <Loader2 className="h-4 w-4 animate-spin" />
            ) : (
              <CheckCheck className="h-4 w-4" />
            )}
            Mark all read
          </Button>
        </div>
      </CardHeader>
      <CardContent className="p-0">
        {isLoading && items.length === 0 ? (
          <div className="space-y-0">
            {Array.from({ length: 5 }).map((_, i) => (
              <div key={i} className="px-4 py-3 border-b">
                <Skeleton className="h-4 w-3/4 mb-2" />
                <Skeleton className="h-3 w-1/3" />
              </div>
            ))}
          </div>
        ) : items.length === 0 ? (
          <EmptyState
            icon={Newspaper}
            title="No items yet"
            description="This source hasn't fetched any items yet. Try refreshing the source."
            action={onRefresh ? { label: 'Refresh Source', onClick: onRefresh } : undefined}
          />
        ) : (
          <>
            <div className="max-h-[600px] overflow-y-auto">
              {items.map((item, index) => (
                <NewsItemRow
                  key={item.id}
                  ref={(el) => {
                    itemRefs.current[index] = el;
                  }}
                  item={item}
                  isSelected={selectedIndex === index}
                  onClick={() => handleRowClick(index)}
                  onExternalLink={(e) => handleExternalLink(e, item)}
                />
              ))}
            </div>
            <div className="p-4 border-t flex items-center justify-between">
              <PaginationControls
                currentPage={currentPage}
                totalPages={totalPages}
                onPageChange={handlePageChange}
                disabled={isLoading}
              />
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

      <ItemDetailPanel
        item={selectedIndex >= 0 ? items[selectedIndex] : null}
        open={isPanelOpen}
        onClose={handleClosePanel}
        onItemUpdated={handleItemUpdated}
      />

      <KeyboardShortcutsHelp
        open={showHelp}
        onClose={() => setShowHelp(false)}
      />
    </Card>
  );
}
