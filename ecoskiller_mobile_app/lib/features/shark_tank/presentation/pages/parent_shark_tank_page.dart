import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';
import 'package:lucide_icons/lucide_icons.dart';
import 'package:animate_do/animate_do.dart';
import 'package:ecoskiller_mobile_app/core/theme/app_theme.dart';

class ParentSharkTankPage extends StatelessWidget {
  final Map<String, dynamic> user;
  const ParentSharkTankPage({super.key, required this.user});

  @override
  Widget build(BuildContext context) {
    bool isDark = Theme.of(context).brightness == Brightness.dark;

    return Scaffold(
      backgroundColor: isDark ? AppTheme.midnightAbyss : AppTheme.skyWhite,
      appBar: AppBar(
        title: Text('SHARK TANK TRACKER', style: GoogleFonts.inter(fontWeight: FontWeight.w900, fontSize: 16, letterSpacing: 2)),
      ),
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(20),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            _buildChildSummaryCard(),
            const SizedBox(height: 32),
            _buildSectionHeader('ACTIVITY TIMELINE'),
            const SizedBox(height: 16),
            _buildTimeline(),
            const SizedBox(height: 32),
            _buildSectionHeader('REWARDS & RECOGNITION'),
            const SizedBox(height: 16),
            _buildRewardsPanel(),
          ],
        ),
      ),
    );
  }

  Widget _buildChildSummaryCard() {
    return Container(
      padding: const EdgeInsets.all(24),
      decoration: BoxDecoration(
        color: AppTheme.linkedinBlue.withOpacity(0.05),
        borderRadius: BorderRadius.circular(24),
        border: Border.all(color: AppTheme.linkedinBlue.withOpacity(0.1)),
      ),
      child: Column(
        children: [
          const CircleAvatar(radius: 40, backgroundColor: AppTheme.linkedinBlue, child: Icon(LucideIcons.user, color: Colors.white, size: 40)),
          const SizedBox(height: 16),
          Text('Aryan Sharma', style: GoogleFonts.inter(fontWeight: FontWeight.bold, fontSize: 20)),
          Text('Grade 10 • Innovation Node active', style: GoogleFonts.inter(color: Colors.grey, fontSize: 12)),
          const SizedBox(height: 24),
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceAround,
            children: [
              _StatItem('IDEAS', '3'),
              _StatItem('PITCHES', '1'),
              _StatItem('SCORE', '9.2'),
            ],
          ),
        ],
      ),
    );
  }

  Widget _buildSectionHeader(String title) {
    return Text(
      title,
      style: GoogleFonts.inter(
        color: AppTheme.linkedinBlue.withOpacity(0.6),
        fontSize: 11,
        fontWeight: FontWeight.w900,
        letterSpacing: 2,
      ),
    );
  }

  Widget _buildTimeline() {
    return Column(
      children: [
        _TimelineItem(
          title: 'Idea Submitted',
          subtitle: 'AI Smart Bin project submitted for Future Tech 2024',
          date: '12 May',
          isLast: false,
          status: 'COMPLETED',
        ),
        _TimelineItem(
          title: 'AI Review Done',
          subtitle: 'Score: 8.5/10. Ready for shortlisting.',
          date: '13 May',
          isLast: false,
          status: 'COMPLETED',
        ),
        _TimelineItem(
          title: 'Expert Interview',
          subtitle: 'Scheduled for 20th May with Mr. Rajesh (Mentor)',
          date: 'Upcoming',
          isLast: true,
          status: 'PENDING',
        ),
      ],
    );
  }

  Widget _buildRewardsPanel() {
    return Container(
      padding: const EdgeInsets.all(20),
      decoration: BoxDecoration(
        gradient: const LinearGradient(colors: [Colors.amber, Colors.orange]),
        borderRadius: BorderRadius.circular(20),
      ),
      child: Row(
        children: [
          const Icon(LucideIcons.trophy, color: Colors.white, size: 32),
          const SizedBox(width: 16),
          Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text('TOP 10% BADGE', style: GoogleFonts.inter(color: Colors.white, fontWeight: FontWeight.bold)),
              Text('National level shortlisting', style: GoogleFonts.inter(color: Colors.white70, fontSize: 12)),
            ],
          ),
        ],
      ),
    );
  }
}

class _TimelineItem extends StatelessWidget {
  final String title;
  final String subtitle;
  final String date;
  final bool isLast;
  final String status;

  const _TimelineItem({required this.title, required this.subtitle, required this.date, required this.isLast, required this.status});

  @override
  Widget build(BuildContext context) {
    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Column(
          children: [
            Icon(status == 'COMPLETED' ? LucideIcons.checkCircle : LucideIcons.circle, size: 20, color: status == 'COMPLETED' ? Colors.green : Colors.grey),
            if (!isLast) Container(width: 2, height: 40, color: Colors.grey.withOpacity(0.2)),
          ],
        ),
        const SizedBox(width: 16),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  Text(title, style: GoogleFonts.inter(fontWeight: FontWeight.bold)),
                  Text(date, style: GoogleFonts.inter(fontSize: 10, color: Colors.grey)),
                ],
              ),
              Text(subtitle, style: GoogleFonts.inter(fontSize: 12, color: Colors.grey)),
              const SizedBox(height: 20),
            ],
          ),
        ),
      ],
    );
  }
}

class _StatItem extends StatelessWidget {
  final String label;
  final String value;
  const _StatItem(this.label, this.value);

  @override
  Widget build(BuildContext context) {
    return Column(
      children: [
        Text(value, style: GoogleFonts.inter(fontWeight: FontWeight.w900, fontSize: 20)),
        Text(label, style: GoogleFonts.inter(fontSize: 10, color: Colors.grey)),
      ],
    );
  }
}
