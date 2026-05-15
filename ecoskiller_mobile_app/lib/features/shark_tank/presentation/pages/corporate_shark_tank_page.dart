import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';
import 'package:lucide_icons/lucide_icons.dart';
import 'package:animate_do/animate_do.dart';
import 'package:ecoskiller_mobile_app/core/theme/app_theme.dart';

class CorporateSharkTankPage extends StatefulWidget {
  final Map<String, dynamic> user;
  const CorporateSharkTankPage({super.key, required this.user});

  @override
  State<CorporateSharkTankPage> createState() => _CorporateSharkTankPageState();
}

class _CorporateSharkTankPageState extends State<CorporateSharkTankPage> {
  @override
  Widget build(BuildContext context) {
    bool isDark = Theme.of(context).brightness == Brightness.dark;

    return Scaffold(
      backgroundColor: isDark ? AppTheme.midnightAbyss : AppTheme.skyWhite,
      body: CustomScrollView(
        slivers: [
          _buildAppBar(context),
          SliverToBoxAdapter(
            child: Padding(
              padding: const EdgeInsets.all(20.0),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  _buildSponsorshipCard(),
                  const SizedBox(height: 32),
                  _buildSectionHeader('INNOVATION SCOUTING'),
                  const SizedBox(height: 16),
                  _buildAcquisitionGrid(),
                  const SizedBox(height: 32),
                  _buildSectionHeader('PARTNER INSTITUTES'),
                  const SizedBox(height: 16),
                  _buildPartnerList(),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildAppBar(BuildContext context) {
    return SliverAppBar(
      expandedHeight: 120,
      pinned: true,
      title: Text(
        'CORPORATE SCOUT',
        style: GoogleFonts.inter(fontWeight: FontWeight.w900, letterSpacing: 2, fontSize: 16),
      ),
    );
  }

  Widget _buildSponsorshipCard() {
    return FadeInUp(
      child: Container(
        padding: const EdgeInsets.all(24),
        decoration: BoxDecoration(
          gradient: const LinearGradient(
            colors: [Color(0xFF232526), Color(0xFF414345)],
            begin: Alignment.topLeft,
            end: Alignment.bottomRight,
          ),
          borderRadius: BorderRadius.circular(24),
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Icon(LucideIcons.award, color: Colors.amber, size: 32),
            const SizedBox(height: 16),
            Text(
              'SPONSOR A ROUND',
              style: GoogleFonts.inter(color: Colors.white, fontWeight: FontWeight.w900, fontSize: 18),
            ),
            const SizedBox(height: 8),
            Text(
              'Boost your brand by sponsoring innovation rounds in top institutes.',
              style: GoogleFonts.inter(color: Colors.white70, fontSize: 13),
            ),
            const SizedBox(height: 24),
            ElevatedButton(
              onPressed: () {},
              style: ElevatedButton.styleFrom(backgroundColor: Colors.amber, foregroundColor: Colors.black),
              child: const Text('CREATE SPONSORSHIP'),
            ),
          ],
        ),
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

  Widget _buildAcquisitionGrid() {
    return GridView.count(
      shrinkWrap: true,
      physics: const NeverScrollableScrollPhysics(),
      crossAxisCount: 2,
      crossAxisSpacing: 16,
      mainAxisSpacing: 16,
      childAspectRatio: 1.3,
      children: [
        _ScoutStat('Acquisition Flags', '4', Colors.orange),
        _ScoutStat('Scouted Ideas', '156', Colors.blue),
      ],
    );
  }

  Widget _buildPartnerList() {
    return Column(
      children: [
        _PartnerItem('IIT Delhi', '12 Active Ideas'),
        const SizedBox(height: 12),
        _PartnerItem('BITS Pilani', '8 Active Ideas'),
      ],
    );
  }
}

class _ScoutStat extends StatelessWidget {
  final String label;
  final String value;
  final Color color;
  const _ScoutStat(this.label, this.value, this.color);

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: color.withOpacity(0.1),
        borderRadius: BorderRadius.circular(20),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Text(value, style: GoogleFonts.inter(fontSize: 24, fontWeight: FontWeight.bold, color: color)),
          Text(label, style: GoogleFonts.inter(fontSize: 10, color: Colors.grey, fontWeight: FontWeight.bold)),
        ],
      ),
    );
  }
}

class _PartnerItem extends StatelessWidget {
  final String name;
  final String status;
  const _PartnerItem(this.name, this.status);

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: Colors.grey.withOpacity(0.05),
        borderRadius: BorderRadius.circular(16),
      ),
      child: Row(
        children: [
          const Icon(LucideIcons.school, color: AppTheme.linkedinBlue),
          const SizedBox(width: 16),
          Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(name, style: GoogleFonts.inter(fontWeight: FontWeight.bold)),
              Text(status, style: GoogleFonts.inter(fontSize: 12, color: Colors.grey)),
            ],
          ),
        ],
      ),
    );
  }
}
