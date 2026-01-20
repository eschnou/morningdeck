import { useState } from 'react';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Textarea } from '@/components/ui/textarea';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';
import { Loader2, Mail } from 'lucide-react';
import { Switch } from '@/components/ui/switch';
import { toast } from '@/hooks/use-toast';
import { apiClient } from '@/lib/api';
import { events, BRIEFS_CHANGED } from '@/lib/events';
import { z } from 'zod';
import type { DayBriefDTO, BriefingFrequency, DayOfWeek } from '@/types';

interface CreateBriefDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onSuccess?: (brief: DayBriefDTO) => void;
}

const TIMEZONES = [
  'UTC',
  'America/New_York',
  'America/Chicago',
  'America/Denver',
  'America/Los_Angeles',
  'Europe/London',
  'Europe/Paris',
  'Europe/Berlin',
  'Asia/Tokyo',
  'Asia/Shanghai',
  'Australia/Sydney',
];

const DAYS_OF_WEEK: { value: DayOfWeek; label: string }[] = [
  { value: 'MONDAY', label: 'Monday' },
  { value: 'TUESDAY', label: 'Tuesday' },
  { value: 'WEDNESDAY', label: 'Wednesday' },
  { value: 'THURSDAY', label: 'Thursday' },
  { value: 'FRIDAY', label: 'Friday' },
  { value: 'SATURDAY', label: 'Saturday' },
  { value: 'SUNDAY', label: 'Sunday' },
];

const briefSchema = z.object({
  title: z.string().min(1, 'Title is required').max(255),
  briefing: z.string().min(1, 'Briefing criteria is required'),
  scheduleTime: z.string().regex(/^\d{2}:\d{2}$/, 'Invalid time format'),
});

export function CreateBriefDialog({ open, onOpenChange, onSuccess }: CreateBriefDialogProps) {
  const [isLoading, setIsLoading] = useState(false);
  const [formData, setFormData] = useState({
    title: '',
    description: '',
    briefing: '',
    frequency: 'DAILY' as BriefingFrequency,
    scheduleDayOfWeek: 'MONDAY' as DayOfWeek,
    scheduleTime: '08:00',
    timezone: 'UTC',
    emailDeliveryEnabled: true,
  });
  const [errors, setErrors] = useState<Record<string, string>>({});

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setErrors({});

    try {
      briefSchema.parse({
        title: formData.title,
        briefing: formData.briefing,
        scheduleTime: formData.scheduleTime,
      });

      setIsLoading(true);

      const brief = await apiClient.createDayBrief({
        title: formData.title,
        description: formData.description || undefined,
        briefing: formData.briefing,
        frequency: formData.frequency,
        scheduleDayOfWeek: formData.frequency === 'WEEKLY' ? formData.scheduleDayOfWeek : undefined,
        scheduleTime: formData.scheduleTime + ':00',
        timezone: formData.timezone,
        emailDeliveryEnabled: formData.emailDeliveryEnabled,
      });

      toast({
        title: 'Brief created',
        description: `Successfully created "${brief.title}"`,
      });

      resetForm();
      onOpenChange(false);
      events.emit(BRIEFS_CHANGED);
      onSuccess?.(brief);
    } catch (error: unknown) {
      if (error instanceof z.ZodError) {
        const fieldErrors: Record<string, string> = {};
        error.errors.forEach((err) => {
          if (err.path[0]) {
            fieldErrors[err.path[0].toString()] = err.message;
          }
        });
        setErrors(fieldErrors);
      } else {
        toast({
          title: 'Failed to create brief',
          description: error instanceof Error ? error.message : 'An error occurred',
          variant: 'destructive',
        });
      }
    } finally {
      setIsLoading(false);
    }
  };

  const resetForm = () => {
    setFormData({
      title: '',
      description: '',
      briefing: '',
      frequency: 'DAILY',
      scheduleDayOfWeek: 'MONDAY',
      scheduleTime: '08:00',
      timezone: 'UTC',
      emailDeliveryEnabled: true,
    });
    setErrors({});
  };

  const handleClose = () => {
    resetForm();
    onOpenChange(false);
  };

  return (
    <Dialog open={open} onOpenChange={handleClose}>
      <DialogContent className="max-w-lg">
        <DialogHeader>
          <DialogTitle>Create Brief</DialogTitle>
          <DialogDescription>
            Set up a new briefing. You can add sources after creating it.
          </DialogDescription>
        </DialogHeader>
        <form onSubmit={handleSubmit}>
          <div className="space-y-4 py-4">
            <div className="space-y-2">
              <Label htmlFor="title">Title *</Label>
              <Input
                id="title"
                placeholder="Daily Tech News"
                value={formData.title}
                onChange={(e) => setFormData({ ...formData, title: e.target.value })}
                disabled={isLoading}
                className={errors.title ? 'border-destructive' : ''}
              />
              {errors.title && (
                <p className="text-sm text-destructive">{errors.title}</p>
              )}
            </div>

            <div className="space-y-2">
              <Label htmlFor="description">Description</Label>
              <Input
                id="description"
                placeholder="Brief description of this briefing"
                value={formData.description}
                onChange={(e) => setFormData({ ...formData, description: e.target.value })}
                disabled={isLoading}
              />
            </div>

            <div className="space-y-2">
              <Label htmlFor="briefing">Briefing Criteria *</Label>
              <Textarea
                id="briefing"
                placeholder="Describe what content should be included..."
                value={formData.briefing}
                onChange={(e) => setFormData({ ...formData, briefing: e.target.value })}
                disabled={isLoading}
                className={errors.briefing ? 'border-destructive' : ''}
                rows={3}
              />
              {errors.briefing && (
                <p className="text-sm text-destructive">{errors.briefing}</p>
              )}
            </div>

            <div className="grid grid-cols-2 gap-3">
              <div className="space-y-2">
                <Label htmlFor="frequency">Frequency</Label>
                <Select
                  value={formData.frequency}
                  onValueChange={(value: BriefingFrequency) =>
                    setFormData({ ...formData, frequency: value })
                  }
                  disabled={isLoading}
                >
                  <SelectTrigger>
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="DAILY">Daily</SelectItem>
                    <SelectItem value="WEEKLY">Weekly</SelectItem>
                  </SelectContent>
                </Select>
              </div>

              {formData.frequency === 'WEEKLY' && (
                <div className="space-y-2">
                  <Label htmlFor="scheduleDayOfWeek">Day</Label>
                  <Select
                    value={formData.scheduleDayOfWeek}
                    onValueChange={(value: DayOfWeek) =>
                      setFormData({ ...formData, scheduleDayOfWeek: value })
                    }
                    disabled={isLoading}
                  >
                    <SelectTrigger>
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      {DAYS_OF_WEEK.map((day) => (
                        <SelectItem key={day.value} value={day.value}>
                          {day.label}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                </div>
              )}

              <div className="space-y-2">
                <Label htmlFor="scheduleTime">Time</Label>
                <Input
                  id="scheduleTime"
                  type="time"
                  value={formData.scheduleTime}
                  onChange={(e) => setFormData({ ...formData, scheduleTime: e.target.value })}
                  disabled={isLoading}
                  className={errors.scheduleTime ? 'border-destructive' : ''}
                />
              </div>

              <div className="space-y-2">
                <Label htmlFor="timezone">Timezone</Label>
                <Select
                  value={formData.timezone}
                  onValueChange={(value) => setFormData({ ...formData, timezone: value })}
                  disabled={isLoading}
                >
                  <SelectTrigger>
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    {TIMEZONES.map((tz) => (
                      <SelectItem key={tz} value={tz}>
                        {tz}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
            </div>

            {/* Email Delivery */}
            <div className="flex items-center justify-between rounded-lg border p-4">
              <div className="space-y-0.5">
                <div className="flex items-center gap-2">
                  <Mail className="h-4 w-4 text-muted-foreground" />
                  <Label htmlFor="emailDeliveryEnabled" className="font-medium">
                    Email report delivery
                  </Label>
                </div>
                <p className="text-sm text-muted-foreground">
                  Send generated reports to your email
                </p>
              </div>
              <Switch
                id="emailDeliveryEnabled"
                checked={formData.emailDeliveryEnabled}
                onCheckedChange={(checked) =>
                  setFormData({ ...formData, emailDeliveryEnabled: checked })
                }
                disabled={isLoading}
              />
            </div>
          </div>
          <DialogFooter>
            <Button type="button" variant="outline" onClick={handleClose} disabled={isLoading}>
              Cancel
            </Button>
            <Button type="submit" disabled={isLoading}>
              {isLoading && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
              Create Brief
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
